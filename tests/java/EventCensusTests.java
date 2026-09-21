import dev.ichinomiya.ninebotenhance.core.EventCensus;
import dev.ichinomiya.ninebotenhance.navi.NaviApps;

final class EventCensusTests {
    static void run() {
        EventCensus census=new EventCensus(3,50);
        int logged=0;for(int i=0;i<120;i++)if(census.shouldLog("a"))logged++;
        CoreTests.check(logged==5&&census.seen("a")==120,"first three then every fiftieth occurrence is logged");
        CoreTests.check(census.shouldLog("b")&&census.census().equals("{a=120,b=1} total=121")&&census.census().isEmpty(),"the census lists counts since the previous census and then resets");
        CoreTests.check(census.seen("b")==1&&!census.shouldLog("a")&&census.seen("a")==121,"totals survive a census");
        CoreTests.check(EventCensus.truncate("ab\ncd",10).equals("ab cd")&&EventCensus.truncate("abcdef",3).equals("abc…(6)")&&EventCensus.truncate(null,3).equals("null"),"truncation is bounded and single-line");
        CoreTests.check(NaviApps.supported(NaviApps.AMAP)&&NaviApps.supported(NaviApps.TENCENT)&&NaviApps.supported(NaviApps.BAIDU)&&!NaviApps.supported("cn.ninebot.ninebot")&&NaviApps.label(NaviApps.BAIDU).equals("baidu"),"the three navigation apps are recognised");
    }
}
