package dev.ichinomiya.ninebotenhance.core;

/**
 * User overrides for the original cast encoder: a forced bitrate and/or frame rate (0 = keep the vehicle configuration) and whether the
 * phone preview paints the live stream statistics. Values are clamped into the supported ranges on construction.
 */
public record EncoderOverride(int bitrateKbps,int fps,boolean previewStats) {
    public static final int MIN_BITRATE_KBPS=250,MAX_BITRATE_KBPS=16000,BITRATE_STEP_KBPS=50;
    public static final int MIN_FPS=5,MAX_FPS=60;
    public static final int DEFAULT_BITRATE_KBPS=1500,DEFAULT_FPS=20;
    public static final EncoderOverride NONE=new EncoderOverride(0,0,false);
    public EncoderOverride {
        bitrateKbps=bitrateKbps<=0?0:Math.max(MIN_BITRATE_KBPS,Math.min(MAX_BITRATE_KBPS,bitrateKbps));
        fps=fps<=0?0:Math.max(MIN_FPS,Math.min(MAX_FPS,fps));
    }
    public boolean overridesBitrate(){return bitrateKbps>0;}
    public boolean overridesFps(){return fps>0;}
    public boolean active(){return overridesBitrate()||overridesFps();}
    /** Bits per second for the encoder, 0 when not overriding. */
    public int bitrateBps(){return bitrateKbps*1000;}
    /** Milliseconds between encoder loop iterations, 0 when not overriding. */
    public int intervalMs(){return fps==0?0:Math.max(1,1000/fps);}
    public EncoderOverride withBitrate(int kbps){return new EncoderOverride(kbps,fps,previewStats);}
    public EncoderOverride withFps(int value){return new EncoderOverride(bitrateKbps,value,previewStats);}
    public EncoderOverride withPreviewStats(boolean value){return new EncoderOverride(bitrateKbps,fps,value);}
    public static String describeBitrate(int kbps){return kbps>=1000?String.format(java.util.Locale.ROOT,"%.2f Mbps",kbps/1000.0):kbps+" kbps";}
    public String describe(){return (overridesBitrate()?"bitrate="+bitrateKbps+"kbps":"bitrate=原配置")+" "+(overridesFps()?"fps="+fps:"fps=原配置")+" previewStats="+previewStats;}
}
