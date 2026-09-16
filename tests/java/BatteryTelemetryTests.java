import dev.ichinomiya.ninebotenhance.core.BatteryTelemetry;
import static dev.ichinomiya.ninebotenhance.core.BatteryTelemetry.Source.*;

final class BatteryTelemetryTests {
    static void run()throws Exception{
        CoreTests.close(BatteryTelemetry.decode("rVoltage",new byte[]{0x3f,0x1c,0x10,0}),72.31f,"pack voltage uses the 10 mV register scale and ignores trailing registers");
        CoreTests.check(BatteryTelemetry.decode("rVoltage",new byte[]{(byte)0xff,(byte)0xff})==null&&BatteryTelemetry.decode("rVoltage",new byte[]{0x10,0})==null,"implausible voltages stay unknown");
        CoreTests.check(BatteryTelemetry.decode("rVoltage",new byte[]{0x3f})==null&&BatteryTelemetry.decode("rVoltage",null)==null,"truncated payloads are ignored");
        CoreTests.check(BatteryTelemetry.decode("rBattery",new byte[]{0x55,0})==null&&!BatteryTelemetry.recognized("rBmsTmp")&&BatteryTelemetry.recognized("rVoltage")&&BatteryTelemetry.recognized("rVrlaVoltage"),"only the verified voltage tags are decoded");
        CoreTests.close(BatteryTelemetry.decode("rVrlaVoltage",new byte[]{0x3f,0x1c}),72.31f,"lead-acid packs report the display-board register at the same 10 mV scale");
        CoreTests.close(BatteryTelemetry.decode("rVoltage2",new byte[]{0x3f,0x1c}),72.31f,"bay two reports on the same scale");
        CoreTests.check(BatteryTelemetry.lithiumBays(new byte[]{0,2})==BatteryTelemetry.BAY2&&BatteryTelemetry.lithiumBays(new byte[]{0,7})==7&&BatteryTelemetry.lithiumBays(new byte[]{(byte)0xff,0})==0&&BatteryTelemetry.lithiumBays(new byte[]{1})==-1,"bay flags are bits 256, 512 and 1024 of rBool");
        CoreTests.check(BatteryTelemetry.voltageCandidates(BatteryTelemetry.BAY2).equals(java.util.List.of("rVrlaVoltage","rVoltage2"))&&BatteryTelemetry.voltageCandidates(BatteryTelemetry.BAY1|BatteryTelemetry.BAY3).equals(java.util.List.of("rVrlaVoltage","rVoltage","rVoltage3")),"the display register comes first, then the occupied bays in order");
        CoreTests.check(BatteryTelemetry.voltageCandidates(0).size()==4&&BatteryTelemetry.voltageCandidates(-1).size()==4&&BatteryTelemetry.displayRegister("rVrlaVoltage")&&!BatteryTelemetry.displayRegister("rVoltage3"),"unknown or empty bays probe every register");
        CoreTests.check(BatteryTelemetry.u16(new byte[]{0x34,0x12},0)==0x1234,"registers are little-endian");
        BatteryTelemetry t=new BatteryTelemetry();t.select("A");
        CoreTests.check(t.snapshot().equals(BatteryTelemetry.EMPTY)&&t.selectedKey().equals("A"),"no data cannot appear as a zero voltage");
        CoreTests.check(!t.update("A",null,BLUETOOTH,1000000,1000)&&!t.update("A",Float.NaN,BLUETOOTH,1000000,1000),"missing or nonfinite readings do not touch the snapshot");
        CoreTests.check(t.update("A",72.3f,BLUETOOTH,1000000,1000),"first BLE voltage is stored");
        var first=t.snapshot();
        CoreTests.check(first.voltage().number()==72.3f&&first.voltage().wallTime()==1000000&&first.voltage().elapsedTime()==1000,"voltage keeps both receipt clocks");
        CoreTests.check(t.update("A",72.3f,BLUETOOTH,1002000,3000),"unchanged voltage in a new BLE reply still refreshes its timestamp");
        CoreTests.check(!t.update("A",70f,SERVER,1003000,4000),"delayed cloud data cannot overwrite a recent BLE voltage");
        CoreTests.check(!t.update("A",60f,BLUETOOTH,1000001,1500),"out-of-order callbacks cannot overwrite newer readings");
        CoreTests.check(!t.update("",70f,BLUETOOTH,1004000,5000),"missing vehicle ignored");
        t.beginSession();t.select("B");t.update("B",60f,BLUETOOTH,1005000,6000);
        CoreTests.check(t.snapshot().voltage().number()==72.3f&&t.selectedKey().equals("A"),"active cast stays pinned to its own vehicle");
        t.endSession();CoreTests.check(t.snapshot().voltage().number()==60f&&t.selectedKey().equals("B"),"next cast follows the newly selected vehicle");
        t.select(null);CoreTests.check(t.snapshot().equals(BatteryTelemetry.EMPTY),"no selected vehicle clears visible readings");
        CoreTests.check(!BatteryTelemetry.stale(first.voltage(),120999)&&BatteryTelemetry.stale(first.voltage(),121000)&&!BatteryTelemetry.stale(null,999999),"old data becomes visually stale at the boundary");
        CoreTests.check(BatteryTelemetry.voltage(first.voltage()).equals("72.3")&&BatteryTelemetry.voltage(null).equals("--"),"voltage uses locale-independent one-decimal formatting");
        BatteryTelemetry many=new BatteryTelemetry();many.select("keep");
        for(int i=0;i<12;i++)many.update("v"+i,50f,BLUETOOTH,1000000+i,1000+i);
        many.update("keep",60f,BLUETOOTH,2000000,2000);
        CoreTests.check(many.snapshot().voltage().number()==60f,"bounded vehicle history never evicts the selected vehicle");
        CoreTests.check(BatteryTelemetry.bayCommands(BatteryTelemetry.BAY3).equals(java.util.List.of("rVoltage3"))&&BatteryTelemetry.bayCommands(0).size()==3&&BatteryTelemetry.probeCandidates(BatteryTelemetry.BAY3).equals(java.util.List.of("rBms3SOC")),"bay registers and SOC probes follow the occupied bays");
    }
}
