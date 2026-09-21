package dev.ichinomiya.ninebotenhance.hook;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.DashboardTheme;
import dev.ichinomiya.ninebotenhance.core.NaviTestData;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Writes dashboard navigation data (TFT board command 113) while a vehicle cast session runs in cruise mode: either the live
 * state relayed from a phone navigation app or, with the test switch on, the scripted route from {@link NaviTestData}. Both go
 * through Ninebot's own DynamicDevice.sendCommand exactly as its map navigation page does: distance and current road once per
 * route, next road on change, info and drive info every second. setNaviStart is Ninebot's; this class never starts or stops a
 * cast and it is the module's only write path. The same path carries the dashboard day/night flag ({@link DashboardTheme}):
 * once per vehicle session and again whenever the user toggles it in the preview toolbar.
 */
public final class NaviSender {
    public static final int LOG_SENDS_PER_COMMAND=2,LOG_REPLIES_PER_COMMAND=2;
    private final FrameClient frames;private final Supplier<Class<?>> deviceClass;private final BooleanSupplier compatible;
    private final Handler worker;
    private final Set<String> logged=ConcurrentHashMap.newKeySet();
    private final Map<String,AtomicInteger> sendLogged=new ConcurrentHashMap<>(),replyLogged=new ConcurrentHashMap<>();
    private final Map<String,Boolean> supportCache=new ConcurrentHashMap<>();
    private final AtomicInteger sent=new AtomicInteger(),replied=new AtomicInteger(),silent=new AtomicInteger();
    private volatile String mode;private volatile long startedAt,lastTick;
    private volatile boolean introSent;private volatile String lastCurrentRoad,lastNextRoad;private volatile int lastTotal=-1;
    public NaviSender(FrameClient frames,Supplier<Class<?>> deviceClass,BooleanSupplier compatible){
        this.frames=frames;this.deviceClass=deviceClass;this.compatible=compatible;
        HandlerThread thread=new HandlerThread("Ninebot-NaviSend");thread.start();worker=new Handler(thread.getLooper());
    }
    /** Scripted test route; called from the frame worker while a vehicle session runs and the test switch is on. */
    public void pulse(String vehicle){
        if(!begin(vehicle,"test"))return;
        long now=SystemClock.elapsedRealtime();if(now-lastTick<NaviTestData.TICK_MS)return;lastTick=now;long elapsed=now-startedAt;
        worker.post(()->{NaviTestData.Step step=NaviTestData.at(elapsed);
            tick(vehicle,NaviTestData.TOTAL_METERS,NaviTestData.CURRENT_ROAD,step.nextRoad(),NaviTestData.info(step),NaviTestData.driveInfo(step),"segment icon="+step.icon()+" next="+step.nextRoad()+" remaining="+step.remaining()+"m");});
    }
    /** Live state from a phone navigation app; called every frame-worker pulse with the latest fresh update. */
    public void pulseLive(String vehicle,NaviUpdate update){
        if(update==null||!begin(vehicle,"live:"+update.source()))return;
        long now=SystemClock.elapsedRealtime();if(now-lastTick<NaviTestData.TICK_MS)return;lastTick=now;
        worker.post(()->tick(vehicle,update.totalDistance(),update.currentRoad(),update.nextRoad(),
            NaviTestData.info(update.remainDistance(),update.remainSeconds(),update.maneuver(),update.segmentRemain(),update.lights(),NaviTestData.GPS_LEVEL),
            NaviTestData.driveInfo(update.drivenDistance(),update.drivenSeconds()),"next icon="+update.maneuver()+" next="+update.nextRoad()+" remaining="+update.remainDistance()+"m"));
    }
    private boolean begin(String vehicle,String newMode){
        if(vehicle==null||vehicle.isEmpty()||deviceClass.get()==null||!compatible.getAsBoolean())return false;
        if(newMode.equals(mode))return true;
        if(mode!=null)frames.report("NAVI send mode "+mode+" -> "+newMode+": "+summary());
        mode=newMode;startedAt=SystemClock.elapsedRealtime();lastTick=0;introSent=false;lastCurrentRoad=null;lastNextRoad=null;lastTotal=-1;
        supportCache.clear();sendLogged.clear();replyLogged.clear();
        frames.report("NAVI send start "+newMode+": cmd 113 via DynamicDevice.sendCommand");
        return true;
    }
    public void stop(){
        if(mode==null)return;String ended=mode;mode=null;
        frames.report("NAVI send stop "+ended+": "+summary());
    }
    public boolean running(){return mode!=null;}
    private volatile Boolean themeSent;
    /** Called every pulse with the chosen theme; sends when it differs from what this session already sent. Null ends the session. */
    public void pulseTheme(String vehicle,Boolean dark){
        if(dark==null){themeSent=null;return;}
        if(vehicle==null||vehicle.isEmpty()||deviceClass.get()==null||!compatible.getAsBoolean()||dark.equals(themeSent))return;
        worker.post(()->{
            if(dark.equals(themeSent))return;Class<?> type=deviceClass.get();if(type==null)return;
            try{
                Object device=connectedDevice(type);if(device==null)return;
                if(!vehicle.equals(string(type.getMethod("getSn").invoke(device))))return;
                send(type,device,DashboardTheme.COMMAND,DashboardTheme.payload(dark));themeSent=dark;
                frames.report("THEME sent "+(dark?"dark":"light")+" via "+DashboardTheme.COMMAND);
            }catch(Throwable e){if(logged.add("theme "+e.getClass().getSimpleName()))frames.report("THEME unavailable "+e.getClass().getSimpleName()+": "+e.getMessage());}
        });
    }
    public String summary(){return "sent="+sent.get()+" replied="+replied.get()+" noReply="+silent.get()+(mode!=null?" "+mode+" "+(SystemClock.elapsedRealtime()-startedAt)/1000+"s":"");}
    private void tick(String vehicle,int total,String currentRoad,String nextRoad,byte[] info,byte[] drive,String change){
        Class<?> type=deviceClass.get();if(type==null||mode==null)return;
        try{
            Object device=connectedDevice(type);
            if(device==null){if(logged.add("disconnected"))frames.report("NAVI send skipped: no connected DynamicDevice");return;}
            String sn=string(type.getMethod("getSn").invoke(device));
            if(!vehicle.equals(sn)){if(logged.add("mismatch "+sn))frames.report("NAVI send skipped: connected sn="+sn+" selected="+vehicle);return;}
            if(!introSent||total!=lastTotal){introSent=true;lastTotal=total;send(type,device,NaviTestData.CMD_DISTANCE,NaviTestData.distance(total));}
            if(currentRoad!=null&&!currentRoad.isEmpty()&&!currentRoad.equals(lastCurrentRoad)){lastCurrentRoad=currentRoad;send(type,device,NaviTestData.CMD_ROAD,NaviTestData.text(currentRoad));}
            if(nextRoad!=null&&!nextRoad.isEmpty()&&!nextRoad.equals(lastNextRoad)){lastNextRoad=nextRoad;send(type,device,NaviTestData.CMD_ROAD_NEXT,NaviTestData.text(nextRoad));frames.report("NAVI send "+change);}
            send(type,device,NaviTestData.CMD_INFO,info);
            send(type,device,NaviTestData.CMD_DRIVE,drive);
        }catch(Throwable e){if(logged.add("tick "+e.getClass().getSimpleName()))frames.report("NAVI send unavailable "+e.getClass().getSimpleName()+": "+e.getMessage());}
    }
    private void send(Class<?> type,Object device,String name,byte[] payload)throws ReflectiveOperationException{
        Boolean known=supportCache.get(name);
        if(known==null){known=Boolean.TRUE.equals(type.getMethod("hasCommand",String.class).invoke(device,name));supportCache.put(name,known);}
        if(!known){if(logged.add("unsupported "+name))frames.report("NAVI send "+name+" absent from this vehicle configuration");return;}
        ClassLoader loader=type.getClassLoader();Class<?> function=Class.forName(HookCatalog.FUNCTION1,false,loader);
        long started=SystemClock.elapsedRealtime();
        Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{function},(proxy,method,args)->{
            switch(method.getName()){
                case "invoke":
                    boolean ok=args!=null&&args.length==1&&args[0]!=null;
                    if(ok)replied.incrementAndGet();else silent.incrementAndGet();
                    if(replyLogged.computeIfAbsent(name,k->new AtomicInteger()).incrementAndGet()<=LOG_REPLIES_PER_COMMAND)frames.report("NAVI send "+(ok?"reply ":"no reply ")+name+" after "+(SystemClock.elapsedRealtime()-started)+"ms");
                    return null;
                case "toString":return "NinebotEnhance navi sender";
                case "hashCode":return System.identityHashCode(proxy);
                case "equals":return args!=null&&args.length==1&&proxy==args[0];
                default:return null;
            }
        });
        type.getMethod("sendCommand",String.class,byte[].class,boolean.class,Integer.class,function).invoke(device,name,payload,false,0,callback);
        sent.incrementAndGet();
        if(sendLogged.computeIfAbsent(name,k->new AtomicInteger()).incrementAndGet()<=LOG_SENDS_PER_COMMAND)frames.report("NAVI send "+name+" bytes="+hex(payload));
    }
    private Object connectedDevice(Class<?> type)throws ReflectiveOperationException{
        Class<?> client=Class.forName(HookCatalog.CLIENT,false,type.getClassLoader());
        Object instance=client.getField("INSTANCE").get(null);
        Object device=client.getMethod("getConnectedDevice").invoke(instance);
        return type.isInstance(device)?device:null;
    }
    private static String string(Object value){return value instanceof String?(String)value:"";}
    private static String hex(byte[] data){if(data==null)return "null";StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(data.length,24);i++)b.append(String.format(Locale.ROOT,"%02x",data[i]));return b.toString();}
}
