package dev.ichinomiya.ninebotenhance.hook;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * One list of the 6.10.10 classes, methods and resources the module depends on. Verified once the target
 * application is attached, so an app update reports exactly which target went missing instead of failing silently.
 * Method parameter patterns use canonical type names; "*" matches any type and "*.Name" any package with that simple name;
 * a null parameter list matches any overload of the name.
 */
public final class HookCatalog {
    public record Target(String kind,String owner,String member,String[] params,String purpose){
        public String label(){
            switch(kind){
                case "method":return simple(owner)+"#"+member+(params==null?"(…)":"("+String.join(",",Arrays.stream(params).map(HookCatalog::simple).toArray(String[]::new))+")");
                case "class":return simple(owner);
                default:return kind+":"+member;
            }
        }
    }
    public record Report(int total,List<String> missing){
        public boolean ok(){return missing.isEmpty();}
        public String text(){return ok()?"Hook 目标 "+total+"/"+total+" 可用":"Hook 目标 "+(total-missing.size())+"/"+total+" 可用，缺失："+String.join("、",missing);}
    }
    public static Target type(String owner,String purpose){return new Target("class",owner,"",null,purpose);}
    public static Target method(String owner,String member,String[] params,String purpose){return new Target("method",owner,member,params,purpose);}
    public static Target resource(String kind,String name,String purpose){return new Target(kind,"",name,null,purpose);}

    public static final String DEVICE="cn.ninebot.library.bluetooth.dynamic.DynamicDevice",CLIENT="cn.ninebot.library.nbbluetooth.NbBluetoothClient",FUNCTION1="kotlin.jvm.functions.Function1";
    public static final String TYRE_PARSER="cn.ninebot.device.motor.thirdparts.TirePressureStateParser",DEVICE_MANAGER="cn.ninebot.device.DeviceManager";
    public static final String NAVI_MESSENGER="cn.ninebot.device.motor.navi.DashNaviDataMessenger",CRUISE_ACTIVITY="cn.ninebot.device.motor.navi.CruiseModeActivity";
    public static final String CAST_MANAGER="cn.ninebot.mapcapture.DeviceScreenCastManager",RTP_SENDER="cn.ninebot.mapcapture.NBBluetoothRtpSender";
    public static final String NAVIGATION_CARD="layout_detail_navigation_card";
    public static final List<Target> ALL=List.of(
        type(DEVICE,"蓝牙读取回复与指令分发"),
        method(DEVICE,"onResponse",new String[]{"*.NbFrame"},"所有蓝牙读取回复的必经点"),
        method(DEVICE,"intercept",new String[]{"*.Command"},"总线统计"),
        method(DEVICE,"sendCommand",new String[]{"java.lang.String","byte[]","boolean","java.lang.Integer",FUNCTION1},"胎压/电压主动读取"),
        method(DEVICE,"hasCommand",new String[]{"java.lang.String"},"按车型配置过滤指令"),
        method(CLIENT,"getConnectedDevice",new String[0],"当前连接的车辆"),
        type(TYRE_PARSER,"胎压解析器"),
        method(TYRE_PARSER,"parseExtraFloat",null,"胎压字段"),
        method(TYRE_PARSER,"init",null,"绑定车辆身份"),
        type(DEVICE_MANAGER,"车辆管理器"),
        method(NAVI_MESSENGER+"$Companion","isPowerOn",null,"投屏前的开机检查"),
        method(CRUISE_ACTIVITY+"$Companion","open",null,"巡航页入口"),
        type(CAST_MANAGER,"原投屏管理器"),
        type(RTP_SENDER,"原蓝牙发送器"),
        type("cn.ninebot.capture.CaptureClient","原采集入口"),
        type("cn.ninebot.capture.codec.BitmapToH264Encoder","原编码器"),
        resource("layout",NAVIGATION_CARD,"投屏按钮所在卡片"),
        resource("id","vMainContainer","卡片容器"),
        resource("id","ivCruise","原巡航按钮"),
        resource("id","layoutHistory","按钮插入锚点"),
        resource("id","layoutNavigation","卡片结构校验"));

    /** Resolve every target; the class resolver may search several class loaders, the resource resolver returns 0 for unknown names. */
    public static Report verify(List<Target> targets,Function<String,Class<?>> classes,ToIntFunction<Target> resources){
        List<String> missing=new ArrayList<>();
        for(Target t:targets){
            boolean present;
            try{
                switch(t.kind()){
                    case "class":present=classes.apply(t.owner())!=null;break;
                    case "method":{Class<?> owner=classes.apply(t.owner());present=owner!=null&&hasMethod(owner,t.member(),t.params());break;}
                    default:present=resources.applyAsInt(t)!=0;
                }
            }catch(RuntimeException|LinkageError e){present=false;}
            if(!present)missing.add(t.label());
        }
        return new Report(targets.size(),missing);
    }
    private static boolean hasMethod(Class<?> owner,String name,String[] params){
        List<Method> candidates=new ArrayList<>();
        try{candidates.addAll(Arrays.asList(owner.getDeclaredMethods()));}catch(LinkageError ignored){}
        try{candidates.addAll(Arrays.asList(owner.getMethods()));}catch(LinkageError ignored){}
        for(Method m:candidates){
            if(!m.getName().equals(name))continue;
            if(params==null)return true;
            Class<?>[] actual=m.getParameterTypes();if(actual.length!=params.length)continue;
            boolean all=true;
            for(int i=0;i<actual.length&&all;i++)all=matches(actual[i],params[i]);
            if(all)return true;
        }
        return false;
    }
    private static boolean matches(Class<?> type,String pattern){
        if(pattern.equals("*"))return true;
        String canonical=type.getCanonicalName()==null?type.getName():type.getCanonicalName();
        return pattern.startsWith("*.")?canonical.endsWith(pattern.substring(1)):canonical.equals(pattern);
    }
    private static String simple(String name){int dot=name.lastIndexOf('.');return dot<0?name:name.substring(dot+1);}
    private HookCatalog(){}
}
