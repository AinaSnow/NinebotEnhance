package dev.ichinomiya.ninebotenhance.core;

import java.util.Locale;

/**
 * Destination of the route a navigation app is guiding right now, with the app's own travel mode, so the same route can be
 * requested again through the app's public URI after the system relaunched it on another display. {@code scene} is AMap's
 * engine NaviSceneType (1 ride, 2 drive, 3 bus, 4 walk, 5 truck, 9 motorbike). {@code receivedAt} is the module service's
 * elapsed-realtime clock when the destination arrived, 0 inside the navigation app.
 */
public record NaviDestination(String source,String name,String poiId,double lat,double lon,int scene,long receivedAt){
    public static final int SCENE_RIDE=1,SCENE_DRIVE=2,SCENE_BUS=3,SCENE_WALK=4,SCENE_TRUCK=5,SCENE_MOTORBIKE=9;
    /** A destination older than this is not re-planned; a ride that long without any turn-by-turn update is over. */
    public static final long FRESH_MS=180000;
    public boolean valid(){return name!=null&&!name.isEmpty()&&Math.abs(lat)<=90&&Math.abs(lon)<=180&&(lat!=0||lon!=0);}
    public boolean fresh(long now){return receivedAt>0&&now-receivedAt<=FRESH_MS;}
    public NaviDestination receivedAt(long at){return new NaviDestination(source,name,poiId,lat,lon,scene,at);}
    /** AMap route-plan type for the scene (RouteType enum: 0 car, 1 bus, 2 walk, 3 ride, 7 truck, 11 motorbike); -1 when unknown. */
    public static int amapRouteType(int scene){
        switch(scene){
            case SCENE_DRIVE:return 0;case SCENE_BUS:return 1;case SCENE_WALK:return 2;case SCENE_RIDE:return 3;case SCENE_TRUCK:return 7;case SCENE_MOTORBIKE:return 11;
            default:return -1;
        }
    }
    /** AMap's documented route-planning URI ({@code amapuri://route/plan/}) for this destination in the same travel mode. */
    public String amapUri(){
        StringBuilder b=new StringBuilder("amapuri://route/plan/?sourceApplication=ninebotenhance&dev=0");
        int type=amapRouteType(scene);if(type>=0)b.append("&t=").append(type);
        b.append("&dlat=").append(String.format(Locale.ROOT,"%.6f",lat)).append("&dlon=").append(String.format(Locale.ROOT,"%.6f",lon));
        b.append("&dname=").append(encode(name));
        if(poiId!=null&&!poiId.isEmpty())b.append("&did=").append(encode(poiId));
        return b.toString();
    }
    public String describe(){return source+" dest="+name+" poi="+poiId+" lat="+lat+" lon="+lon+" scene="+scene;}
    static String encode(String value){
        try{return java.net.URLEncoder.encode(value,"UTF-8");}catch(java.io.UnsupportedEncodingException e){return "";}
    }
}
