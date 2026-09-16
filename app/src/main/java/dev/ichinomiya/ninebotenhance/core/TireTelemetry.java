package dev.ichinomiya.ninebotenhance.core;

import java.util.LinkedHashMap;
import java.util.Locale;

/** In-process read-only measurements, isolated by vehicle. Times describe receipt, not sensor sampling. */
public final class TireTelemetry {
    public enum Source { BLUETOOTH, SERVER }
    @FunctionalInterface public interface ServerValues {Object get(String key)throws ReflectiveOperationException;}
    public record Value(float number,long wallTime,long elapsedTime,Source source) {}
    public record Wheel(Value pressure,Value temperature) {
        public long updatedWall(){return pressure==null?temperature==null?0:temperature.wallTime():temperature==null?pressure.wallTime():Math.min(pressure.wallTime(),temperature.wallTime());}
    }
    public record Snapshot(Wheel front,Wheel rear) {
        /** Oldest receipt among the present fields (monotonic clock), or -1 when nothing was received. */
        public long oldestReceipt(){
            long oldest=-1;
            for(Value value:new Value[]{front.pressure(),front.temperature(),rear.pressure(),rear.temperature()}){if(value==null)continue;oldest=oldest<0?value.elapsedTime():Math.min(oldest,value.elapsedTime());}
            return oldest;
        }
    }
    public static final Wheel EMPTY_WHEEL=new Wheel(null,null);
    /** Verified 6.10.10 read command polled by the detail page; module ble, register 138, ten bytes. */
    public static final String REALTIME_COMMAND="rTirePressureRealTimeInfo";
    public record RealTime(Float frontPressure,Float frontTemperature,Float rearPressure,Float rearTemperature){}
    public static final Snapshot EMPTY=new Snapshot(EMPTY_WHEEL,EMPTY_WHEEL);
    private final LinkedHashMap<String,Snapshot> vehicles=new LinkedHashMap<>();
    private String selected="",pinned="";private boolean session;

    public synchronized void select(String key){selected=key==null?"":key;}
    public synchronized void beginSession(){pinned=selected;session=true;}
    public synchronized void endSession(){session=false;pinned="";}
    public synchronized Snapshot snapshot(){return vehicles.getOrDefault(session?pinned:selected,EMPTY);}
    public boolean readServer(String key,ServerValues values,long wall,long elapsed)throws ReflectiveOperationException{
        boolean changed=false;
        for(int i=0;i<2;i++){
            String prefix="tp_list["+i+"].";Object position=values.get(prefix+"tp_position");
            int wheel=position instanceof Number?((Number)position).intValue():"1".equals(position)?1:"2".equals(position)?2:0;
            if(wheel!=1&&wheel!=2)continue;
            Object pressure=values.get(prefix+"tire_pressure"),temperature=values.get(prefix+"tp_temperature");
            Float p=pressure instanceof Number?((Number)pressure).floatValue():null,t=temperature instanceof Number?((Number)temperature).floatValue():null;
            changed|=update(key,wheel==1,p,t,Source.SERVER,wall,elapsed);
        }
        return changed;
    }
    public synchronized boolean update(String key,boolean front,Float pressure,Float temperature,Source source,long wall,long elapsed){
        if(key==null||key.isEmpty()||source==null||wall<=0||elapsed<0)return false;
        Snapshot previous=vehicles.getOrDefault(key,EMPTY);Wheel wheel=front?previous.front():previous.rear();
        Value nextPressure=merge(wheel.pressure(),pressure!=null&&pressure>=0?pressure:null,source,wall,elapsed);
        Value nextTemperature=merge(wheel.temperature(),temperature,source,wall,elapsed);
        Wheel next=new Wheel(nextPressure,nextTemperature);if(next.equals(wheel))return false;
        vehicles.put(key,front?new Snapshot(next,previous.rear()):new Snapshot(previous.front(),next));
        if(vehicles.size()>8){for(String candidate:vehicles.keySet().toArray(new String[0]))if(!candidate.equals(selected)&&!candidate.equals(pinned)&&!candidate.equals(key)){vehicles.remove(candidate);break;}}
        return true;
    }
    private static Value merge(Value old,Float number,Source source,long wall,long elapsed){
        if(number==null||!Float.isFinite(number))return old;
        if(old!=null){
            if(elapsed<old.elapsedTime())return old;
            // Repeated server/cache values cannot make an old measurement look newly reported.
            if(source==Source.SERVER&&(Float.compare(number,old.number())==0||old.source()==Source.BLUETOOTH&&elapsed-old.elapsedTime()<30000))return old;
        }
        return new Value(number,wall,elapsed,source);
    }
    /** Raw zero means no sensor sample on this vehicle family, never an actual 0.00 bar or -40 degree reading. */
    public static Float bluetoothPressure(Number raw){if(raw==null)return null;float value=raw.floatValue()*.02f;return Float.isFinite(value)&&value>0?value:null;}
    public static Float bluetoothTemperature(Number raw){if(raw==null||raw.floatValue()==0)return null;float value=raw.floatValue()-40;return Float.isFinite(value)?value:null;}
    /**
     * M5P tire_pressure feature layout: register 0 holds alarm bits, register 1 holds rear (low byte) and front
     * (high byte) pressure, register 2 the same order for temperature; the native parser then applies the
     * 0.02 bar and -40 degree conversions used above.
     */
    public static RealTime decodeRealTime(byte[] data){
        if(data==null||data.length<6)return null;
        return new RealTime(bluetoothPressure(data[3]&0xff),bluetoothTemperature(data[5]&0xff),bluetoothPressure(data[2]&0xff),bluetoothTemperature(data[4]&0xff));
    }
    public static String pressure(Value value){return value==null?"--":String.format(Locale.ROOT,"%.1f",value.number());}
    public static String temperature(Value value){return value==null?"--":String.format(Locale.ROOT,"%.0f",value.number());}
    public static boolean stale(Value value,long now){return value!=null&&now-value.elapsedTime()>=120000;}
    private static String age(Value value,long now){return value==null?"missing":value.source()+":"+Math.max(0,now-value.elapsedTime())+"ms";}
    public synchronized String summary(long now){Snapshot s=snapshot();return "frontPressure="+age(s.front().pressure(),now)+" frontTemperature="+age(s.front().temperature(),now)+" rearPressure="+age(s.rear().pressure(),now)+" rearTemperature="+age(s.rear().temperature(),now);}
}
