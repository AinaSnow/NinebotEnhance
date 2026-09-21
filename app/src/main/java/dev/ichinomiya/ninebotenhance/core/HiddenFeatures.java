package dev.ichinomiya.ninebotenhance.core;

import java.util.Set;

/**
 * Ninebot features the module forces on: settings entries hidden by the vehicle's feature bits (identified by the title key of
 * their dynamic UI configuration) and the dashboard hard-key card Ninebot only lays out for vehicles with an external meter.
 */
public record HiddenFeatures(boolean throttle,boolean hardkey,boolean cruise) {
    public static final HiddenFeatures NONE=new HiddenFeatures(false,false,false);
    /** Title keys of the bidirectional-throttle entries in device_ui_accelerator: reverse, seamless reverse, assisted braking. */
    public static final Set<String> THROTTLE_TITLES=Set.of("string.neg_throttle_reverse","string.seamless_reverse","string.neg_throttle_brake");
    /** Ninebot's dynamic view type ("subtype.model") of the hard-key remote card and the configuration it is built from. */
    public static final String HARDKEY_TYPE="ext_meter_virtual_key.common_card";
    public static final String HARDKEY_CONFIG="{\"type\":\""+HARDKEY_TYPE+"\",\"title\":\"仪表按键\"}";
    public boolean any(){return throttle||hardkey||cruise;}
    /** Whether a settings entry with this title key must be shown regardless of the vehicle's feature bits. */
    public boolean forces(String title){return title!=null&&throttle&&THROTTLE_TITLES.contains(title);}
    public HiddenFeatures withThrottle(boolean value){return new HiddenFeatures(value,hardkey,cruise);}
    public HiddenFeatures withHardkey(boolean value){return new HiddenFeatures(throttle,value,cruise);}
    /** Ninebot's own cruise button on the vehicle card, hidden when the vehicle is not flagged as supporting cruise. */
    public HiddenFeatures withCruise(boolean value){return new HiddenFeatures(throttle,hardkey,value);}
    public String describe(){return "throttle="+throttle+" hardkey="+hardkey+" cruise="+cruise;}
}
