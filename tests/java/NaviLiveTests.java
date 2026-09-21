import dev.ichinomiya.ninebotenhance.core.AmapNavi;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;

final class NaviLiveTests {
    static final String AMAP_EVENT_1="{\"eventType\":1,\"pathID\":736256989,\"routeTotalDist\":6521,\"routeRemainDist\":6521,\"drivenDist\":0,\"drivenTBTDist\":0,\"driveTime\":0,\"routeRemainTime\":725,\"curRouteName\":\"黄河十七路\",\"nextRouteName\":\"黄河十六路\",\"nextRouteNameForCrossImage\":\"黄河十六路\",\"notAvoidInfo\":{\"type\":0,\"distToCar\":0,\"forbidType\":0,\"valid\":0},\"segmentRemainDist\":664,\"speed\":-1.000000,\"averageSpeed\":0.000000,\"curManeuverID\":3,\"curLinkRoadClass\":8,\"curLinkFormway\":15,\"split\":0,\"curSegIdx\":0,\"curLinkID\":\"5123090063900541522\",\"routeRemainLightCount\":11,\"routeRemainIntersectionCount\":4,\"viaInfo\":{\"poiRemainDist\":0,\"poiRemainTime\":0,\"poiRemainTrafficlightNum\":0,\"extInfos\":\"\"}}";
    static void run() {
        NaviUpdate u=AmapNavi.parse(AMAP_EVENT_1);
        CoreTests.check(u!=null&&u.source().equals("amap")&&u.totalDistance()==6521&&u.remainDistance()==6521&&u.remainSeconds()==725&&u.drivenDistance()==0&&u.drivenSeconds()==0,"AMap event 1 distances and times are read");
        CoreTests.check(u.currentRoad().equals("黄河十七路")&&u.nextRoad().equals("黄河十六路")&&u.segmentRemain()==664&&u.maneuver()==3&&u.lights()==11&&u.receivedAt()==0,"road names, segment distance, maneuver and lights are read");
        CoreTests.check(AmapNavi.parse("{\"eventType\":3,\"tip\":\"右转\",\"remainSegmentLen\":700,\"maneuverID\":3}")==null&&AmapNavi.parse("not json")==null&&AmapNavi.parse(null)==null&&AmapNavi.parse("{\"eventType\":1}")==null,"other events, junk and incomplete payloads are ignored");
        NaviUpdate stamped=u.receivedAt(10_000);
        CoreTests.check(stamped.fresh(10_000)&&stamped.fresh(18_000)&&!stamped.fresh(18_001)&&!u.fresh(5)&&!stamped.arrived(),"freshness follows the receive time");
        CoreTests.check(new NaviUpdate("x",100,0,0,100,50,"","",0,9,0,1).arrived()&&new NaviUpdate("x",100,10,5,90,50,"","",10,15,0,1).arrived(),"arrival is the arrived icon or nothing left of a known route");
        CoreTests.check(u.describe().contains("remain=6521m/725s")&&u.describe().contains("icon=3"),"the description names the fields");
    }
}
