package dev.ichinomiya.ninebotenhance.core;

/** Debounces an empty display. Black pixels and a paused Activity are not evidence of an empty task list. */
public final class AppRecoveryState {
    public static final int HIDDEN = 0, MISSING = 1, RESTARTING = 2;
    private long graceUntil, emptySince = -1;
    private int state;
    public void started(long now) { state = HIDDEN; emptySince = -1; graceUntil = now + 3000; }
    public int state() { return state; }
    public void sample(long now, boolean occupied) {
        if (occupied) { state = HIDDEN; emptySince = -1; return; }
        if (now < graceUntil) return;
        if (emptySince < 0) emptySince = now;
        if (now - emptySince >= 1200) state = MISSING;
    }
    public void unknown() { state = HIDDEN; emptySince = -1; }
    public boolean restart(long now) {
        if (state != MISSING) return false;
        started(now); state = RESTARTING; return true;
    }
    public void failed() { state = MISSING; emptySince = -1; }
}
