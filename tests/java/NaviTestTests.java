import dev.ichinomiya.ninebotenhance.core.NaviTestData;
import java.util.Arrays;

final class NaviTestTests {
    static void run() {
        NaviTestData.Step start=NaviTestData.at(0);
        CoreTests.check(start.remaining()==3000&&start.driven()==0&&start.icon()==9&&start.stepRemaining()==500&&start.lights()==3&&start.nextRoad().equals("九号大道")&&!start.arrived(),"the route starts at 3 km with a straight icon");
        NaviTestData.Step later=NaviTestData.at(70_000);
        CoreTests.check(later.driven()==560&&later.remaining()==2440&&later.remainingSeconds()==305&&later.icon()==2&&later.stepRemaining()==440&&later.nextRoad().equals("科技园路")&&later.elapsedSeconds()==70,"the second segment turns left towards the next road");
        NaviTestData.Step end=NaviTestData.at(10*60_000);
        CoreTests.check(end.arrived()&&end.remaining()==0&&end.driven()==3000&&end.icon()==15&&end.stepRemaining()==0&&end.nextRoad().equals("目的地")&&end.lights()==1,"after the route the arrived icon is held");
        CoreTests.check(NaviTestData.at(-5).equals(start),"negative time is clamped to the start");
        byte[] info=NaviTestData.info(2440,305,2,440,3,1);
        CoreTests.check(info.length==18&&Arrays.equals(info,new byte[]{(byte)0x88,0x09,0,0,0x31,0x01,0,0,0x02,0x00,(byte)0xb8,0x01,0,0,0x03,0x00,0x01,0x00}),"navi info is the 18-byte little-endian layout of DashNaviDataMessenger");
        CoreTests.check(Arrays.equals(NaviTestData.info(later),info),"a step encodes to the same bytes");
        CoreTests.check(Arrays.equals(NaviTestData.distance(3000),new byte[]{(byte)0xb8,0x0b,0,0}),"distance is a little-endian int");
        CoreTests.check(Arrays.equals(NaviTestData.driveInfo(560,70),new byte[]{0x30,0x02,0,0,0x46,0,0,0}),"drive info is driven metres then elapsed seconds");
        byte[] road=NaviTestData.text("测试路");
        CoreTests.check(road.length==10&&road[9]==0&&new String(road,0,9,java.nio.charset.StandardCharsets.UTF_8).equals("测试路"),"road names are UTF-8 with a trailing zero");
        CoreTests.check(NaviTestData.ICONS.length==NaviTestData.NEXT_ROADS.length&&NaviTestData.TOTAL_METERS/NaviTestData.SEGMENT_METERS==NaviTestData.NEXT_ROADS.length,"one icon and one next road per 500 m segment");
    }
}
