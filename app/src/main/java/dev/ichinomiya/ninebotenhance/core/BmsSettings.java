package dev.ichinomiya.ninebotenhance.core;

/** The bound DL BMS: its address and how often FC17 is polled while something watches the link. */
public record BmsSettings(String mac,int pollMs){
    public static final int MIN_POLL_MS=1000,MAX_POLL_MS=10000,DEFAULT_POLL_MS=2000,POLL_STEP_MS=500;
    public static final BmsSettings NONE=new BmsSettings("",DEFAULT_POLL_MS);
    public BmsSettings{
        mac=LampSettings.normalizeMac(mac);
        pollMs=Math.max(MIN_POLL_MS,Math.min(MAX_POLL_MS,pollMs/POLL_STEP_MS*POLL_STEP_MS));
    }
    public boolean bound(){return LampSettings.validMac(mac);}
    /** Readings older than this are shown as unknown. */
    public long limitMs(){return pollMs*3L+1000;}
    public BmsSettings withMac(String value){return new BmsSettings(value,pollMs);}
    public BmsSettings withPollMs(int value){return new BmsSettings(mac,value);}
    public String label(){return bound()?mac+" · "+(pollMs%1000==0?pollMs/1000+"":String.format(java.util.Locale.ROOT,"%.1f",pollMs/1000f))+" 秒":"未绑定";}
}
