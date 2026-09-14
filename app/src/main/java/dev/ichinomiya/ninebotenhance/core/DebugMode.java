package dev.ichinomiya.ninebotenhance.core;

/** Hidden option state; revealing the checkbox does not change the current picture. */
public final class DebugMode {
    private volatile boolean unlocked, enabled;
    private int taps;
    public void restore(boolean unlocked, boolean enabled) { this.unlocked = unlocked; this.enabled = unlocked && enabled; taps = 0; }
    public boolean unlocked() { return unlocked; }
    public boolean enabled() { return enabled; }
    public boolean tapVersion() { if (!unlocked && ++taps >= 5) unlocked = true; return unlocked; }
    public boolean setEnabled(boolean value) {
        value &= unlocked;
        if (enabled == value) return false;
        enabled = value; return true;
    }
}
