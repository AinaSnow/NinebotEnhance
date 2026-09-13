package dev.ichinomiya.ninebotenhance.ipc;

public final class Protocol {
    public static final String VERSION = "1.0.0";
    public static final int VERSION_CODE = 34;
    public static final String DISPLAY_NAME = "Ninebot Enhance Display";
    public static final String DAEMON_CLASS = "dev.ichinomiya.ninebotenhance.display.RootDisplayMain";
    public static final String MODULE = "dev.ichinomiya.ninebotenhance", TARGET = "cn.ninebot.ninebot";
    public static final String DESCRIPTOR = MODULE + ".VirtualDisplay.v5", ROOT_AUTHORITY = MODULE + ".root";
    public static final String REQUEST = "mirror_request", TAG = "NinebotEnhance";
    public static final int READ = 1, REPORT = 2, STOP_DIRECT = 3, BEGIN = 4, SETTINGS = 5, LOG = 6;
    public static final int UI_BACK = 8, UI_INPUT = 9, UI_RESTART_APP = 10, APP_ICON = 11;
    public static final int UI_TEXT = 12, UI_TYPING_KEY = 13, UI_DELETE = 14;
    public static final int PRIVILEGE = 15;
    public static final int PROJECTION_SURFACE = 16;
    public static final int LOG_EXPORT_BEGIN = 17, LOG_EXPORT_FINISH = 18, LOG_EXPORT_CANCEL = 19;
    public static final String SCREEN_CAPTURE = "screen_capture", CAPTURE_WIDTH = "capture_width", CAPTURE_HEIGHT = "capture_height";
    public static final String CAPTURE_REVISION = "capture_revision", CAPTURE_CONSENT = "capture_consent";
    public static final int ROOT_STOP = 30, ROOT_INPUT = 32, ROOT_KEY = 33, ROOT_RESTART_APP = 34;
    public static final int ROOT_TEXT = 35, ROOT_TYPING_KEY = 36, ROOT_DELETE = 37;
    public static final String APP_RECOVERY = "appRecovery", APP_RECOVERY_DETAIL = "appRecoveryDetail";
    public static final String APP_LAYOUT_POLICY = "appLayoutPolicy";
    public static boolean validRequest(String value) { return value != null && value.matches("[a-f0-9]{32}"); }
    private Protocol() {}
}
