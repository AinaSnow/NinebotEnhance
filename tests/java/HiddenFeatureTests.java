import dev.ichinomiya.ninebotenhance.core.HiddenFeatures;

final class HiddenFeatureTests {
    static void run() {
        HiddenFeatures none=HiddenFeatures.NONE;
        CoreTests.check(!none.any()&&!none.forces("string.neg_throttle_reverse")&&!none.hardkey(),"nothing is forced by default");
        HiddenFeatures throttle=none.withThrottle(true);
        CoreTests.check(throttle.any()&&throttle.forces("string.neg_throttle_reverse")&&throttle.forces("string.seamless_reverse")&&throttle.forces("string.neg_throttle_brake"),"the three bidirectional-throttle rows are forced");
        CoreTests.check(!throttle.forces("string.throttle_calibration_title")&&!throttle.forces(null)&&!throttle.forces(""),"other rows and missing titles are untouched");
        HiddenFeatures hardkey=none.withHardkey(true);
        CoreTests.check(hardkey.any()&&hardkey.hardkey()&&!hardkey.throttle()&&!hardkey.forces("string.neg_throttle_reverse"),"the hard-key card unlock does not touch settings rows");
        CoreTests.check(throttle.withThrottle(false).equals(none)&&throttle.withHardkey(true).describe().equals("throttle=true hardkey=true cruise=false"),"unlocks can be cleared and described");
        CoreTests.check(none.withCruise(true).any()&&none.withCruise(true).cruise()&&!none.withCruise(true).hardkey()&&!none.withCruise(true).forces("string.neg_throttle_reverse"),"the cruise-button unlock is independent");
        CoreTests.check(HiddenFeatures.HARDKEY_CONFIG.contains("\"type\":\""+HiddenFeatures.HARDKEY_TYPE+"\"")&&HiddenFeatures.HARDKEY_TYPE.equals("ext_meter_virtual_key.common_card"),"the card is built from Ninebot's own view type");
    }
}
