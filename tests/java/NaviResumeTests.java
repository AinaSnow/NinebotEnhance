import dev.ichinomiya.ninebotenhance.core.AmapNavi;
import dev.ichinomiya.ninebotenhance.core.NaviDestination;
import dev.ichinomiya.ninebotenhance.core.NaviResume;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;

final class NaviResumeTests {
    static final String AMAP_EVENT_59="{\"eventType\":59,\"start\":{\"name\":\"我的位置\",\"poiid\":\"\",\"new_type\":\"\",\"pathId\":0,\"segmentIdx\":0,\"lon\":118.030230,\"lat\":37.433620,\"x\":222227446,\"y\":104077135,\"uniqueId\":29},\"end\":{\"name\":\"淄博站\",\"poiid\":\"B0215026BL\",\"new_type\":\"150200\",\"pathId\":0,\"segmentIdx\":0,\"lon\":118.056401,\"lat\":36.786688,\"x\":222246961,\"y\":104682034,\"uniqueId\":30},\"via\":[],\"viaRemained\":[],\"highlightPathID\":2286324563,\"routePreference\":32,\"naviid\":\"573bfa00a1da1f3d2ca3e8925216222e\",\"contentOptions\":\"0x2A01001E\"}";
    static void run() {
        NaviDestination d=AmapNavi.parseDestination(AMAP_EVENT_59,NaviDestination.SCENE_MOTORBIKE);
        CoreTests.check(d!=null&&d.source().equals("amap")&&d.name().equals("淄博站")&&d.poiId().equals("B0215026BL")&&Math.abs(d.lat()-36.786688)<1e-9&&Math.abs(d.lon()-118.056401)<1e-9&&d.scene()==9&&d.receivedAt()==0,"AMap event 59 yields the route end with the scene");
        String uri=d.amapUri();
        CoreTests.check(uri.startsWith("amapuri://route/plan/?sourceApplication=ninebotenhance&dev=0&t=11&dlat=36.786688&dlon=118.056401&dname=%E6%B7%84%E5%8D%9A%E7%AB%99&did=B0215026BL"),"the URI carries the motorbike type, coordinates, encoded name and POI id: "+uri);
        CoreTests.check(new NaviDestination("amap","A","",1,2,NaviDestination.SCENE_DRIVE,0).amapUri().contains("&t=0&")&&!new NaviDestination("amap","A","",1,2,77,0).amapUri().contains("&t=")&&!new NaviDestination("amap","A","",1,2,77,0).amapUri().contains("did="),"unknown scenes omit the type and empty POI ids are omitted");
        CoreTests.check(AmapNavi.parseDestination("{\"eventType\":59,\"end\":{\"name\":\"X\"}}",9)==null&&AmapNavi.parseDestination("{\"eventType\":1}",9)==null&&AmapNavi.parseDestination("junk",9)==null,"an end without coordinates, other events and junk yield nothing");
        CoreTests.check(!new NaviDestination("amap","","",1,2,9,0).valid()&&!new NaviDestination("amap","A","",0,0,9,0).valid()&&d.valid(),"validity needs a name and a non-zero coordinate");
        NaviUpdate live=new NaviUpdate("amap",80677,80000,6900,677,60,"a","b",34,2,90,100_000);
        NaviDestination stamped=d.receivedAt(90_000);
        CoreTests.check(NaviResume.amapUri(stamped,live,110_000)!=null&&NaviResume.amapUri(stamped,live,220_000)!=null&&NaviResume.amapUri(stamped,live,220_001)==null,"resume needs a turn-by-turn update within the window");
        CoreTests.check(NaviResume.amapUri(stamped,live.receivedAt(0),110_000)==null&&NaviResume.amapUri(null,live,110_000)==null&&NaviResume.amapUri(d,live,110_000)==null,"no update, no destination or an unstamped destination yields nothing");
        CoreTests.check(NaviResume.amapUri(stamped,new NaviUpdate("amap",80677,0,0,80677,6900,"a","",0,15,0,100_000),110_000)==null,"an arrived navigation is not re-planned");
        CoreTests.check(NaviResume.amapUri(stamped,live,90_000+NaviDestination.FRESH_MS+1)==null,"a stale destination is not re-planned");
        CoreTests.check(NaviResume.amapUri(new NaviDestination("tencent","A","",1,2,9,90_000),live,110_000)==null,"only AMap destinations map to the AMap URI");
    }
}
