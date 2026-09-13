package dev.ichinomiya.ninebotenhance.display;

import dev.ichinomiya.ninebotenhance.ipc.Ipc;

import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.Display;
import java.lang.reflect.*;
import java.util.function.Consumer;

/** Session-owned power hold. Only the verified non-default display group may be woken. */
public final class RootDisplayPower {
    private final Display target;
    private final DisplayManager displays;
    private final int displayId;
    private final Handler handler;
    private final Consumer<String> logger;
    private final IBinder token = new Binder();
    private final IBinder cpuToken = new Binder();
    private final Object service, info;
    private final Method readInfo, acquire, release, wake, interactive;
    private final Field groupField;
    private int groupId = -1;
    private boolean held, closed;
    private long lastWake = -10000;
    private String lastStatus = "";
    private final Runnable check = new Runnable() {
        @Override public void run() { tick(); }
    };

    public RootDisplayPower(Display target, DisplayManager displays, Handler handler, Consumer<String> logger) throws Exception {
        this.target = target; this.displays = displays; this.handler = handler; this.logger = logger;
        displayId = target.getDisplayId();
        if (displayId <= 0) throw new IllegalArgumentException("电源保持只允许独立虚拟屏");
        Class<?> infoType = Class.forName("android.view.DisplayInfo");
        info = infoType.getConstructor().newInstance(); groupField = infoType.getField("displayGroupId");
        readInfo = Display.class.getMethod("getDisplayInfo", infoType);
        IBinder binder = (IBinder)Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "power");
        service = Class.forName("android.os.IPowerManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
        Class<?> type = Class.forName("android.os.IPowerManager");
        acquire = type.getMethod("acquireWakeLock", IBinder.class, int.class, String.class, String.class,
                WorkSource.class, String.class, int.class, Class.forName("android.os.IWakeLockCallback"));
        release = type.getMethod("releaseWakeLock", IBinder.class, int.class);
        // Never substitute the global wakeUp overload, even if the vendor omits this method.
        wake = type.getMethod("wakeUpWithDisplayId", long.class, int.class, String.class, String.class, int.class);
        interactive = type.getMethod("isDisplayInteractive", int.class);
    }
    public synchronized void start() throws Exception {
        if (closed || held) return;
        requireOwnGroup();
        // Keep this group's display awake; a separate partial lock lets recovery run even
        // after a power-key action overrides screen locks and sleeps every group.
        // No ACQUIRE_CAUSES_WAKEUP / ON_AFTER_RELEASE / default or INVALID_DISPLAY fallback.
        // Set held first so cleanup also releases a token after an uncertain Binder outcome.
        held = true;
        try {
            acquire.invoke(service, cpuToken, PowerManager.PARTIAL_WAKE_LOCK, "NinebotMirror:VirtualDisplayCpu",
                    "com.android.shell", null, null, displayId, null);
            acquire.invoke(service, token, PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "NinebotMirror:VirtualDisplay",
                    "com.android.shell", null, null, displayId, null);
            tick();
        } catch (Exception e) { close(); throw e; }
    }
    private void requireOwnGroup() throws Exception {
        if (target.getDisplayId() != displayId || !Boolean.TRUE.equals(readInfo.invoke(target, info)))
            throw new IllegalStateException("虚拟屏已移除");
        int observed = groupField.getInt(info);
        if (observed <= 0 || (groupId > 0 && observed != groupId))
            throw new SecurityException("虚拟屏不在原独立显示组，拒绝保持电源");
        groupId = observed;
    }
    private synchronized void tick() {
        if (closed || !held) return;
        handler.removeCallbacks(check);
        try {
            requireOwnGroup();
            boolean awake = Boolean.TRUE.equals(interactive.invoke(service, displayId));
            int state = target.getState();
            Display phone = displays.getDisplay(Display.DEFAULT_DISPLAY);
            String status = "id=" + displayId + " group=" + groupId + " held=true vd=" + state
                    + " interactive=" + awake + " phone=" + (phone == null ? -1 : phone.getState());
            if (!lastStatus.equals(status)) { logger.accept("POWER " + status); lastStatus = status; }
            long now = SystemClock.uptimeMillis();
            if ((!awake || state == Display.STATE_OFF) && now - lastWake >= 5000) {
                lastWake = now;
                requireOwnGroup();
                wake.invoke(service, now, 2 /* WAKE_REASON_APPLICATION */, "NinebotMirror:VirtualDisplay", "com.android.shell", displayId);
                logger.accept("POWER wake requested id=" + displayId + " group=" + groupId);
            }
        } catch (Exception e) {
            logger.accept("POWER stopped: " + Ipc.error(e));
            // If identity/group validation or a privileged call fails, relinquish only our token.
            close(); return;
        }
        if (!closed) handler.postDelayed(check, 2000);
    }
    public synchronized void close() {
        if (closed) return;
        closed = true; handler.removeCallbacks(check);
        if (held) {
            held = false;
            for (IBinder heldToken : new IBinder[]{token, cpuToken}) {
                try { release.invoke(service, heldToken, 0); }
                catch (Exception e) { logger.accept("POWER release: " + Ipc.error(e)); }
            }
            logger.accept("POWER release requested id=" + displayId);
        }
        // The helper process exits with the session, so Binder death also releases this token.
    }
}
