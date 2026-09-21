package dev.ichinomiya.ninebotenhance.core;

import dev.ichinomiya.ninebotenhance.ipc.Protocol;

public final class CallerPolicy {
    public static boolean allowed(int callerUid, int ownerUid, String[] packages) {
        if (callerUid == ownerUid) return true;
        if (packages != null) for (String name : packages) if (Protocol.TARGET.equals(name)) return true;
        return false;
    }
    /** Navigation apps may only report log lines and publish navigation state. */
    public static boolean naviApp(String[] packages) {
        if (packages != null) for (String name : packages) if (dev.ichinomiya.ninebotenhance.navi.NaviApps.supported(name)) return true;
        return false;
    }
    public static boolean allowedFor(int code, int callerUid, int ownerUid, String[] packages) {
        return allowed(callerUid, ownerUid, packages) || (code == Protocol.REPORT || code == Protocol.NAVI_UPDATE) && naviApp(packages);
    }
    private CallerPolicy() {}
    public static boolean controls(int callerUid, int sessionOwnerUid, boolean matchingReadySession) {
        return matchingReadySession && callerUid >= 10000 && callerUid == sessionOwnerUid;
    }
}
