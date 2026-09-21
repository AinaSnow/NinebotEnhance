package dev.ichinomiya.ninebotenhance.diagnostics;

import java.util.*;

/** Per-session counters with a two-second rolling rate. No pixels, payloads or device identifiers retained. */
public final class StreamStats {
    public final long started;
    private long ended, captured, replaced, encoded, encodedBytes, packets, sentBytes, frames, submittedFrames, submittedBytes;
    private long dropped, lastQueueTotal, lossReports, cumulativeLost, requestedBitrate, requestedFps;
    private int lossFraction;
    private String encoderSource = "", senderSource = "", submitSource = "";
    private final long[][] buckets = new long[11][6]; // epoch, captured, encoded, encoded bytes, packet bytes, submitted frame bytes
    private final LinkedHashSet<String> frameEnds = new LinkedHashSet<>();
    public StreamStats(long now) { started = now; for (long[] b : buckets) b[0] = -1; }
    private long[] bucket(long now) {
        long epoch = now / 200; long[] b = buckets[(int)(epoch % buckets.length)];
        if (b[0] != epoch) { Arrays.fill(b, 0); b[0] = epoch; } return b;
    }
    public synchronized void captured(long now) { if (ended == 0) { captured++; bucket(now)[1]++; } }
    public synchronized void replaced() { if (ended == 0) replaced++; }
    public synchronized void encoded(long now, String source, int bytes) {
        if (ended != 0 || bytes <= 0) return;
        if (encoderSource.isEmpty()) encoderSource = source; if (!encoderSource.equals(source)) return;
        encoded++; encodedBytes += bytes; long[] b = bucket(now); b[2]++; b[3] += bytes;
    }
    /** One RTP packet handed to the transport (UDP datagram or BLE write), header included. */
    public synchronized void packet(long now, String source, int bytes, String frameKey, boolean marker) {
        if (ended != 0 || bytes <= 0) return;
        // Pin the first actually observed RTP submission method, avoiding nested sender-layer double counting.
        if (senderSource.isEmpty()) senderSource = source; if (!senderSource.equals(source)) return;
        packets++; sentBytes += bytes; bucket(now)[4] += bytes;
        if (marker && frameEnds.add(frameKey)) { frames++; if (frameEnds.size() > 128) frameEnds.remove(frameEnds.iterator().next()); }
    }
    /** One encoded frame handed to the original sender before packetization. */
    public synchronized void frameSubmitted(long now, String source, int bytes) {
        if (ended != 0 || bytes <= 0) return;
        if (submitSource.isEmpty()) submitSource = source; if (!submitSource.equals(source)) return;
        submittedFrames++; submittedBytes += bytes; bucket(now)[5] += bytes;
    }
    /** Absolute give-up counter of the original send queue; it restarts from zero whenever the queue is cleared. */
    public synchronized void queueDrops(long total) {
        if (ended != 0 || total < 0) return;
        if (total < lastQueueTotal) lastQueueTotal = 0;
        dropped += total - lastQueueTotal; lastQueueTotal = total;
    }
    /** The dashboard reported packet loss through RTCP APP subtype 1. */
    public synchronized void lossReport() { if (ended == 0) lossReports++; }
    /** RTCP receiver report from the dashboard: loss fraction in 1/256 units and cumulative packets lost (negative = unknown). */
    public synchronized void receiverReport(int fraction, long cumulative) {
        if (ended != 0) return;
        lossFraction = Math.max(0, Math.min(255, fraction)); if (cumulative >= 0) cumulativeLost = cumulative;
    }
    /** The dashboard asked for a new frame rate (RTCP APP subtype 2) or bitrate (subtype 3). */
    public synchronized void dashboardRequest(int subtype, long value) {
        if (ended != 0 || value < 0) return;
        if (subtype == 2) requestedFps = value; else if (subtype == 3) requestedBitrate = value;
    }
    public synchronized void stop(long now) { if (ended == 0) ended = Math.max(started, now); }
    public synchronized Snapshot snapshot(long now) {
        long end = ended == 0 ? now : ended, duration = Math.max(0, end - started);
        long[] sums = new long[5]; long epoch = now / 200;
        if (ended == 0) for (long[] b : buckets) if (b[0] >= Math.max(0, epoch - 9) && b[0] <= epoch)
            for (int i = 0; i < 5; i++) sums[i] += b[i + 1];
        double seconds = Math.max(.2, Math.min(2.0, (now - started) / 1000.0));
        return new Snapshot(duration,captured,replaced,encoded,encodedBytes,packets,sentBytes,frames,
                sums[0]/seconds,sums[1]/seconds,sums[2]*8/seconds,sums[3]*8/seconds,encoderSource,senderSource,ended!=0,
                submittedFrames,submittedBytes,sums[4]*8/seconds,submitSource,
                dropped,lossReports,lossFraction,cumulativeLost,requestedBitrate,requestedFps);
    }
    public record Snapshot(long durationMs,long captured,long replaced,long encoded,long encodedBytes,long packets,long sentBytes,long sentFrames,
                           double captureFps,double encodeFps,double encodeBps,double sendBps,String encoderSource,String senderSource,boolean stopped,
                           long submittedFrames,long submittedBytes,double submitBps,String submitSource,
                           long dropped,long lossReports,int lossFraction,long cumulativeLost,long requestedBitrate,long requestedFps) {
        /** Loss reported by the dashboard's last receiver report, in percent. */
        public double lossPercent() { return lossFraction * 100.0 / 256; }
        /** Wire-level rate when RTP packets are observed, otherwise the frame-level submission rate; negative when neither is observed. */
        public double bandwidthBps() { return !senderSource.isEmpty() ? sendBps : !submitSource.isEmpty() ? submitBps : -1; }
    }
}
