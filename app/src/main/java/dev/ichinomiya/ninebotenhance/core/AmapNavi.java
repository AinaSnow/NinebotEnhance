package dev.ichinomiya.ninebotenhance.core;

import java.util.Map;

/**
 * AMap's guidance engine event 1 (navigation info) as broadcast to {@code NaviEventReceiver.onNaviEvent}; its maneuver IDs use
 * the same numbering as Ninebot's NaviState, so they pass through unchanged. Event 59 (route calculated) carries the start,
 * end and via points; its end becomes a {@link NaviDestination}. Every other event type yields null.
 */
public final class AmapNavi {
    public static final int EVENT_NAVI_INFO=1,EVENT_ROUTE_POINTS=59;
    public static NaviUpdate parse(String json){
        if(json==null||json.indexOf("\"eventType\"")<0)return null;
        Map<String,Object> root;
        try{root=Json.object(Json.parse(json));}catch(RuntimeException e){return null;}
        if(root==null||(int)Json.number(root.get("eventType"),-1)!=EVENT_NAVI_INFO)return null;
        int total=intValue(root,"routeTotalDist"),remain=intValue(root,"routeRemainDist"),seconds=intValue(root,"routeRemainTime");
        int driven=intValue(root,"drivenDist"),driveTime=intValue(root,"driveTime"),segment=intValue(root,"segmentRemainDist");
        int maneuver=intValue(root,"curManeuverID"),lights=intValue(root,"routeRemainLightCount");
        if(total<0||remain<0||segment<0)return null;
        return new NaviUpdate("amap",total,remain,Math.max(0,seconds),Math.max(0,driven),Math.max(0,driveTime),
                NaviUpdate.clean(Json.string(root.get("curRouteName"))),NaviUpdate.clean(Json.string(root.get("nextRouteName"))),segment,Math.max(0,maneuver),Math.max(0,lights),0);
    }
    public static NaviDestination parseDestination(String json,int scene){
        if(json==null||json.indexOf("\"eventType\"")<0)return null;
        Map<String,Object> root;
        try{root=Json.object(Json.parse(json));}catch(RuntimeException e){return null;}
        if(root==null||(int)Json.number(root.get("eventType"),-1)!=EVENT_ROUTE_POINTS)return null;
        Map<String,Object> end=Json.object(root.get("end"));if(end==null)return null;
        double lat=Json.number(end.get("lat"),Double.NaN),lon=Json.number(end.get("lon"),Double.NaN);
        if(Double.isNaN(lat)||Double.isNaN(lon))return null;
        NaviDestination destination=new NaviDestination("amap",NaviUpdate.clean(Json.string(end.get("name"))),NaviUpdate.clean(Json.string(end.get("poiid"))),lat,lon,scene,0);
        return destination.valid()?destination:null;
    }
    private static int intValue(Map<String,Object> map,String key){double v=Json.number(map.get(key),-1);return v>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)v;}
    private AmapNavi(){}
}
