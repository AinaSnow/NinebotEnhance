package dev.ichinomiya.ninebotenhance.core;

public enum PrivilegeMode {
    AUTO, ROOT, SHIZUKU, NONE;
    public boolean usesVirtualDisplay() { return this != NONE; }
    public static PrivilegeMode parse(String name) {
        try { return valueOf(name); } catch (Exception e) { throw new IllegalArgumentException("无效的授权方式"); }
    }
    public boolean useShizuku(boolean authorized) {
        if (this == SHIZUKU && !authorized) throw new IllegalStateException("请先启动并授权 Shizuku / Sui");
        return this == SHIZUKU || this == AUTO && authorized;
    }
}
