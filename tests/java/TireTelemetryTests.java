import dev.ichinomiya.ninebotenhance.core.TireTelemetry;
import static dev.ichinomiya.ninebotenhance.core.TireTelemetry.Source.*;

final class TireTelemetryTests {
    static void run()throws Exception{
        CoreTests.close(TireTelemetry.bluetoothPressure(120),2.4f,"original BLE pressure scale is applied once");
        CoreTests.close(TireTelemetry.bluetoothTemperature(65),25,"original BLE temperature offset is applied once");
        CoreTests.check(TireTelemetry.bluetoothPressure(Float.NaN)==null&&TireTelemetry.bluetoothTemperature(Float.POSITIVE_INFINITY)==null,"malformed BLE numbers stay unknown");
        CoreTests.check(TireTelemetry.bluetoothPressure(0)==null&&TireTelemetry.bluetoothTemperature(0)==null,"a raw zero is an absent sensor sample, not a reading");
        var frame=TireTelemetry.decodeRealTime(new byte[]{0,0,(byte)130,120,70,65,0,0,0,0});
        CoreTests.check(frame!=null,"ten-byte real-time frame decodes");
        CoreTests.close(frame.frontPressure(),2.4f,"front pressure is the high byte of register 1 times 0.02");
        CoreTests.close(frame.frontTemperature(),25,"front temperature is the high byte of register 2 minus 40");
        CoreTests.close(frame.rearPressure(),2.6f,"rear pressure is the low byte of register 1 times 0.02");
        CoreTests.close(frame.rearTemperature(),30,"rear temperature is the low byte of register 2 minus 40");
        var partial=TireTelemetry.decodeRealTime(new byte[]{0,0,0,120,0,65,0,0,0,0});
        CoreTests.check(partial.rearPressure()==null&&partial.rearTemperature()==null&&partial.frontPressure()!=null,"a missing rear sensor leaves only the front wheel");
        CoreTests.check(TireTelemetry.decodeRealTime(new byte[]{0,0,1,2,3})==null&&TireTelemetry.decodeRealTime(null)==null,"short real-time frames are ignored");
        TireTelemetry t=new TireTelemetry();t.select("A");
        CoreTests.check(t.snapshot().equals(TireTelemetry.EMPTY),"missing sensor data cannot appear as zero pressure");
        CoreTests.check(t.update("A",true,2.4f,25f,SERVER,1000000,1000),"server values are already bar and Celsius");
        var front=t.snapshot().front();CoreTests.check(front.pressure().number()==2.4f&&front.temperature().number()==25f,"front pressure and temperature retained together");
        CoreTests.check(t.snapshot().rear().equals(TireTelemetry.EMPTY_WHEEL),"front update cannot populate rear readings");
        CoreTests.check(!t.update("A",true,2.4f,25f,SERVER,1001000,2000)&&front.equals(t.snapshot().front()),"repeated server values do not refresh receipt times");
        CoreTests.check(t.update("A",true,2.4f,null,BLUETOOTH,1002000,3000),"unchanged pressure in a new BLE report still refreshes pressure timestamp");
        CoreTests.check(t.snapshot().front().temperature().elapsedTime()==1000&&t.snapshot().front().updatedWall()==1000000,"partial pressure update cannot make an old temperature look fresh");
        CoreTests.check(!t.update("A",true,2.1f,null,SERVER,1003000,4000),"delayed cloud data cannot overwrite a recent BLE pressure");
        CoreTests.check(!t.update("A",true,2.1f,null,BLUETOOTH,1000001,1500),"out-of-order callbacks cannot overwrite newer readings");
        CoreTests.check(!t.update("A",false,-1f,Float.NaN,SERVER,1003000,4000),"negative pressure and nonfinite temperature ignored");
        t.beginSession();t.select("B");t.update("B",true,2.8f,35f,BLUETOOTH,1005000,6000);
        CoreTests.check(t.snapshot().front().pressure().number()==2.4f,"active cast stays pinned to its own vehicle");
        t.endSession();CoreTests.check(t.snapshot().front().pressure().number()==2.8f,"next cast follows the newly selected vehicle");
        t.select(null);CoreTests.check(t.snapshot().equals(TireTelemetry.EMPTY),"no selected vehicle clears visible readings");
        t.select("A");t.update("A",false,2.6f,27f,SERVER,1100000,101000);
        CoreTests.check(t.snapshot().oldestReceipt()==1000,"the card age follows the oldest field still on display, not the newest wheel");
        CoreTests.check(t.snapshot().rear().pressure().number()==2.6f&&t.snapshot().front().pressure().number()==2.4f,"rear update preserves independent front values");
        CoreTests.check(!TireTelemetry.stale(front.pressure(),120999)&&TireTelemetry.stale(front.pressure(),121000),"old data becomes visually stale at the boundary");
        CoreTests.check(TireTelemetry.pressure(front.pressure()).equals("2.4")&&TireTelemetry.temperature(front.temperature()).equals("25"),"readings use the single-line one-decimal and integer precision");
        CoreTests.check(TireTelemetry.EMPTY.oldestReceipt()==-1,"no readings have no receipt age");
        CoreTests.check(TireTelemetry.pressure(null).equals("--")&&TireTelemetry.temperature(null).equals("--"),"missing fields display placeholders");
        TireTelemetry reversed=new TireTelemetry();reversed.select("A");
        java.util.Map<String,Object> response=java.util.Map.of("tp_list[0].tp_position","2","tp_list[0].tire_pressure",2.6,"tp_list[0].tp_temperature",30,
                "tp_list[1].tp_position",1,"tp_list[1].tire_pressure",2.4,"tp_list[1].tp_temperature",29);
        CoreTests.check(reversed.readServer("A",response::get,1000000,1000),"actual response field names provide both wheel measurements");
        CoreTests.close(reversed.snapshot().front().pressure().number(),2.4f,"server wheel position, not array order, identifies the front tyre");
        CoreTests.close(reversed.snapshot().rear().pressure().number(),2.6f,"server wheel position identifies the rear tyre");
        CoreTests.check(!reversed.readServer("A",key->null,1001000,2000),"unrelated server replies cannot mark tyre readings fresh");
        CoreTests.check(!reversed.readServer("A",key->key.endsWith("tp_position")?3:999,1001000,2000),"unknown wheel positions ignored");
        CoreTests.check(reversed.snapshot().front().pressure().elapsedTime()==1000,"invalid response does not change receipt time");
    }
}
