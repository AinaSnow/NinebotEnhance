package dev.ichinomiya.ninebotenhance.core;

/** Exact selected backend only. A lost Root connection must be checked before deciding it is denied. */
public final class StartPermission {
    public enum Backend { NONE, ROOT, SHIZUKU, MEDIA_PROJECTION }
    public static Backend select(PrivilegeMode mode, boolean shizukuReady, boolean rootReady) {
        if (mode == PrivilegeMode.NONE) return Backend.MEDIA_PROJECTION;
        if (mode == PrivilegeMode.SHIZUKU) return shizukuReady ? Backend.SHIZUKU : Backend.NONE;
        if (mode == PrivilegeMode.ROOT) return rootReady ? Backend.ROOT : Backend.NONE;
        if (mode != PrivilegeMode.AUTO) throw new IllegalArgumentException("Missing privilege mode");
        return shizukuReady ? Backend.SHIZUKU : rootReady ? Backend.ROOT : Backend.NONE;
    }
    public static boolean needsRootCheck(PrivilegeMode mode, boolean shizukuReady, boolean rootReady) {
        return select(mode, shizukuReady, rootReady) == Backend.NONE && mode != PrivilegeMode.SHIZUKU;
    }
    /** One user-initiated preflight, including asynchronous connection recovery and cancellation. */
    public static final class Check {
        public enum Result { STALE, WAIT, START, SETTINGS, TIMEOUT }
        private long generation, deadline;
        private boolean active;
        public long begin(long now) { active = true; deadline = now + 50000; return ++generation; }
        public boolean active() { return active; }
        public boolean owns(long value) { return active && generation == value; }
        public void cancel() { active = false; generation++; }
        public Result accept(long value, boolean allowed, boolean pending, long now) {
            if (!owns(value)) return Result.STALE;
            Result result = now >= deadline ? Result.TIMEOUT : allowed ? Result.START : pending ? Result.WAIT : Result.SETTINGS;
            if (result != Result.WAIT) active = false;
            return result;
        }
    }
    private StartPermission() {}
}
