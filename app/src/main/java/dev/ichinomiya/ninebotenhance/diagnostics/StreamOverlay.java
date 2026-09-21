package dev.ichinomiya.ninebotenhance.diagnostics;

import java.util.*;

/** Text of the phone-preview statistics panel: encoder bitrate, sent bandwidth, frame rate and counts, drops and loss. Phone only, never encoded. */
public final class StreamOverlay {
    public static final String UNKNOWN="--";
    /**
     * @param stats     current session snapshot, null before any session
     * @param targetFps encoder target (override or vehicle configuration), null when not read yet
     * @param vehicle   whether this session sends to the vehicle; the local simulation has no RTP transport
     */
    public static List<String> lines(StreamStats.Snapshot stats,Double targetFps,boolean vehicle) {
        if(stats==null)return List.of("尚未开始");
        boolean encoder=!stats.encoderSource().isEmpty();
        List<String> out=new ArrayList<>();
        String first="码率 "+(encoder?rate(stats.encodeBps()):UNKNOWN);
        if(vehicle){double bandwidth=stats.bandwidthBps();first+="  带宽 "+(bandwidth<0?UNKNOWN:rate(bandwidth));}
        out.add(first);
        String fps=(encoder?decimal(stats.encodeFps()):decimal(stats.captureFps()))+(targetFps==null?"":" / "+trim(targetFps));
        out.add((encoder?"帧率 ":"采集 ")+fps+" fps  帧数 "+(encoder?stats.encoded():stats.captured()));
        if(vehicle) {
            String loss="丢帧 "+stats.dropped()+"  丢包 "+stats.lossReports()+" 次";
            if(stats.lossFraction()>0||stats.cumulativeLost()>0)loss+="  "+decimal(stats.lossPercent())+"% / "+stats.cumulativeLost();
            out.add(loss);
        }
        if(stats.stopped())out.add("已结束");
        return out;
    }
    public static String rate(double bits) { return bits>=1000000?String.format(Locale.ROOT,"%.2f Mbps",bits/1000000):String.format(Locale.ROOT,"%.0f kbps",bits/1000); }
    private static String decimal(double value) { return String.format(Locale.ROOT,"%.1f",value); }
    private static String trim(double value) { return value==Math.rint(value)?String.valueOf((long)value):decimal(value); }
    private StreamOverlay() {}
}
