package dev.ichinomiya.ninebotenhance.core;

/** Single-worker schedule: defer early notifications, read the latest queued image at the deadline. */
public final class FramePacer {
    public static final int TARGET_FPS = 20;
    public static final long INTERVAL_MS = 1000 / TARGET_FPS;
    public static final class Ticket {
        public final long delayMs;
        private Ticket(long delayMs) { this.delayMs = delayMs; }
    }
    private long nextRead;
    private Ticket pending;
    public Ticket schedule(long now) {
        if (pending != null) return null;
        pending = new Ticket(Math.max(0, nextRead - now));
        return pending;
    }
    public boolean dispatch(Ticket ticket) {
        if (ticket == null || ticket != pending) return false;
        pending = null;
        return true;
    }
    /** Only a successfully copied frame advances the limit; now is its read start, not copy finish. */
    public void captured(long now) { nextRead = now + INTERVAL_MS; }
    public void reset() { pending = null; nextRead = 0; }
}
