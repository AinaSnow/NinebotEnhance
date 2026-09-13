package dev.ichinomiya.ninebotenhance.diagnostics;

import java.util.*;

/** Per-session counters with a two-second rolling rate. No pixels, payloads or device identifiers retained. */
public final class StreamStats {
    public final long started;
    private long ended, captured, replaced, encoded, encodedBytes, packets, sentBytes, frames;
    private String encoderSource = "", senderSource = "";
    private final long[][] buckets = new long[11][5]; // epoch, captured, encoded, encoded bytes, submitted bytes
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
    public synchronized void packet(long now, String source, int bytes, String frameKey, boolean marker) {
        if (ended != 0 || bytes <= 0) return;
        // Pin the first actually observed RTP submission method, avoiding nested sender-layer double counting.
        if (senderSource.isEmpty()) senderSource = source; if (!senderSource.equals(source)) return;
        packets++; sentBytes += bytes; bucket(now)[4] += bytes;
        if (marker && frameEnds.add(frameKey)) { frames++; if (frameEnds.size() > 128) frameEnds.remove(frameEnds.iterator().next()); }
    }
    public synchronized void stop(long now) { if (ended == 0) ended = Math.max(started, now); }
    public synchronized Snapshot snapshot(long now) {
        long end = ended == 0 ? now : ended, duration = Math.max(0, end - started);
        long[] sums = new long[4]; long epoch = now / 200;
        if (ended == 0) for (long[] b : buckets) if (b[0] >= Math.max(0, epoch - 9) && b[0] <= epoch)
            for (int i = 0; i < 4; i++) sums[i] += b[i + 1];
        double seconds = Math.max(.2, Math.min(2.0, (now - started) / 1000.0));
        return new Snapshot(duration,captured,replaced,encoded,encodedBytes,packets,sentBytes,frames,
                sums[0]/seconds,sums[1]/seconds,sums[2]*8/seconds,sums[3]*8/seconds,encoderSource,senderSource,ended!=0);
    }
    public record Snapshot(long durationMs,long captured,long replaced,long encoded,long encodedBytes,long packets,long sentBytes,long sentFrames,
                           double captureFps,double encodeFps,double encodeBps,double sendBps,String encoderSource,String senderSource,boolean stopped) {}
}
