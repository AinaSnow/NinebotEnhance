package dev.ichinomiya.ninebotenhance.navi;

import android.os.Bundle;
import dev.ichinomiya.ninebotenhance.core.Json;
import dev.ichinomiya.ninebotenhance.core.NaviDestination;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;
import java.util.Map;

/** Bundle and compact-JSON forms of a {@link NaviUpdate} for the module IPC and the loopback channel. */
public final class NaviUpdates {
    public static Bundle toBundle(NaviUpdate u){
        Bundle b=new Bundle();
        b.putString("source",u.source());b.putInt("total",u.totalDistance());b.putInt("remain",u.remainDistance());b.putInt("remain_s",u.remainSeconds());
        b.putInt("driven",u.drivenDistance());b.putInt("driven_s",u.drivenSeconds());b.putString("cur",u.currentRoad());b.putString("next",u.nextRoad());
        b.putInt("segment",u.segmentRemain());b.putInt("icon",u.maneuver());b.putInt("lights",u.lights());b.putLong("received",u.receivedAt());
        return b;
    }
    public static NaviUpdate fromBundle(Bundle b){
        if(b==null||!b.containsKey("remain"))return null;
        return new NaviUpdate(b.getString("source",""),b.getInt("total"),b.getInt("remain"),b.getInt("remain_s"),b.getInt("driven"),b.getInt("driven_s"),
                b.getString("cur",""),b.getString("next",""),b.getInt("segment"),b.getInt("icon"),b.getInt("lights"),b.getLong("received"));
    }
    /** Compact single-line JSON for the loopback channel; road names are escaped. */
    public static String toJson(NaviUpdate u){
        StringBuilder b=new StringBuilder("{");
        field(b,"source",u.source());b.append(',');num(b,"total",u.totalDistance());b.append(',');num(b,"remain",u.remainDistance());b.append(',');
        num(b,"remain_s",u.remainSeconds());b.append(',');num(b,"driven",u.drivenDistance());b.append(',');num(b,"driven_s",u.drivenSeconds());b.append(',');
        field(b,"cur",u.currentRoad());b.append(',');field(b,"next",u.nextRoad());b.append(',');num(b,"segment",u.segmentRemain());b.append(',');
        num(b,"icon",u.maneuver());b.append(',');num(b,"lights",u.lights());
        return b.append('}').toString();
    }
    public static NaviUpdate fromJson(String json){
        Map<String,Object> root;try{root=Json.object(Json.parse(json));}catch(RuntimeException e){return null;}
        if(root==null||!root.containsKey("remain"))return null;
        return new NaviUpdate(str(root,"source"),i(root,"total"),i(root,"remain"),i(root,"remain_s"),i(root,"driven"),i(root,"driven_s"),
                str(root,"cur"),str(root,"next"),i(root,"segment"),i(root,"icon"),i(root,"lights"),0);
    }
    public static Bundle toBundle(NaviDestination d){
        Bundle b=new Bundle();b.putString("kind","dest");b.putString("source",d.source());b.putString("name",d.name());b.putString("poi",d.poiId());
        b.putDouble("lat",d.lat());b.putDouble("lon",d.lon());b.putInt("scene",d.scene());b.putLong("received",d.receivedAt());return b;
    }
    public static NaviDestination destinationFromBundle(Bundle b){
        if(b==null||!"dest".equals(b.getString("kind")))return null;
        return new NaviDestination(b.getString("source",""),b.getString("name",""),b.getString("poi",""),b.getDouble("lat"),b.getDouble("lon"),b.getInt("scene"),b.getLong("received"));
    }
    public static String toJson(NaviDestination d){
        StringBuilder b=new StringBuilder("{");field(b,"kind","dest");b.append(',');field(b,"source",d.source());b.append(',');field(b,"name",d.name());b.append(',');
        field(b,"poi",d.poiId());b.append(",\"lat\":").append(d.lat()).append(",\"lon\":").append(d.lon()).append(',');num(b,"scene",d.scene());
        return b.append('}').toString();
    }
    /** Non-null only for a destination line; turn-by-turn lines go through {@link #fromJson}. */
    public static NaviDestination destinationFromJson(String json){
        if(json==null||json.indexOf("\"kind\":\"dest\"")<0)return null;
        Map<String,Object> root;try{root=Json.object(Json.parse(json));}catch(RuntimeException e){return null;}
        if(root==null||!"dest".equals(Json.string(root.get("kind"))))return null;
        NaviDestination d=new NaviDestination(str(root,"source"),str(root,"name"),str(root,"poi"),Json.number(root.get("lat"),Double.NaN),Json.number(root.get("lon"),Double.NaN),i(root,"scene"),0);
        return Double.isNaN(d.lat())||Double.isNaN(d.lon())?null:d;
    }
    private static int i(Map<String,Object> m,String k){return (int)Json.number(m.get(k),0);}
    private static String str(Map<String,Object> m,String k){String v=Json.string(m.get(k));return v==null?"":v;}
    private static void num(StringBuilder b,String key,int value){b.append('"').append(key).append("\":").append(value);}
    private static void field(StringBuilder b,String key,String value){b.append('"').append(key).append("\":\"").append(escape(value)).append('"');}
    private static String escape(String value){
        if(value==null)return "";StringBuilder b=new StringBuilder();
        for(int i=0;i<value.length();i++){char c=value.charAt(i);
            switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;
                default:if(c<0x20)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}
        return b.toString();
    }
    private NaviUpdates(){}
}
