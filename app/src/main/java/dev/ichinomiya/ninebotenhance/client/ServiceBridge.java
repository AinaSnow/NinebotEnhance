package dev.ichinomiya.ninebotenhance.client;

import dev.ichinomiya.ninebotenhance.core.BindingState;
import dev.ichinomiya.ninebotenhance.ipc.Ipc;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;
import dev.ichinomiya.ninebotenhance.service.FrameBridgeService;

import android.content.*;
import android.os.*;
import java.util.function.Consumer;

/** Application-scoped binding with a callback deadline, death handling and generation checks. */
public final class ServiceBridge {
    public static final String SERVICE_CLASS = FrameBridgeService.class.getName();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BindingState binding = new BindingState();
    private final Consumer<String> log;
    private Context context;
    private Link current;
    private volatile IBinder remote;
    private volatile String state = "等待连接模块服务";
    /** The system refused the bind or several binds went unanswered: on HyperOS that is the autostart permission. */
    private volatile boolean bindRefused;
    public boolean bindRefused() { return bindRefused; }
    public ServiceBridge(Consumer<String> log) { this.log = log; }
    /** MIUI / HyperOS only let another app start our service once autostart (关联启动) is allowed; say so instead of retrying silently. */
    public static final String AUTOSTART_HINT = "HyperOS / MIUI 需要在“应用管理 → Ninebot Enhance → 自启动”中允许自启动和关联启动，否则九号无法拉起模块服务；授予通知使用权后模块进程由系统保持存活，也可绕过此限制。";
    private static Boolean hyperOs;
    public static synchronized boolean hyperOs() {
        if (hyperOs != null) return hyperOs;
        boolean detected = false;
        try {
            java.lang.reflect.Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            for (String key : new String[]{"ro.mi.os.version.name", "ro.miui.ui.version.name", "ro.miui.ui.version.code"}) {
                Object value = get.invoke(null, key); if (value != null && !value.toString().isEmpty()) { detected = true; break; }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
        String brand = (Build.BRAND + " " + Build.MANUFACTURER).toLowerCase(java.util.Locale.ROOT);
        hyperOs = detected || brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco");
        return hyperOs;
    }
    private static String refused(String base) { return hyperOs() ? base + "\n" + AUTOSTART_HINT : base; }
    public void attach(Context context) {
        main.post(() -> { if (this.context != null) return; this.context = context; rotate(); main.postDelayed(health, 1000); });
    }
    public String status() { return state; }
    public boolean connected() { IBinder value = remote; return value != null && value.isBinderAlive(); }
    public void ensure() { main.post(() -> { if (context != null && !connected() && (current == null || binding.expired(SystemClock.elapsedRealtime()))) rotate(); }); }
    public Bundle call(int code, Bundle args) throws RemoteException {
        IBinder value = remote;
        if (value == null || !value.isBinderAlive()) { ensure(); throw new RemoteException("模块服务正在重连：" + state); }
        try { return Ipc.call(value, code, args); }
        catch (DeadObjectException e) { main.post(() -> lost(value, "Binder 已死亡")); throw e; }
        catch (RemoteException e) { if (!value.isBinderAlive()) main.post(() -> lost(value, "Binder 连接失效")); throw e; }
    }
    private void lost(IBinder value, String reason) {
        if (value != remote || current == null) return;
        remote = null; current.unlink(); binding.disconnected(current.id, SystemClock.elapsedRealtime());
        state = reason + "，等待重新连接"; log.accept("BRIDGE " + state);
    }
    private final Runnable health = new Runnable() {
        @Override public void run() {
            if (remote != null && !remote.isBinderAlive()) lost(remote, "服务进程已退出");
            if (context != null && !connected() && (current == null || binding.expired(SystemClock.elapsedRealtime()))) rotate();
            main.postDelayed(this, 1000);
        }
    };
    private void rotate() {
        Link previous = current;
        Link next = new Link(binding.begin(SystemClock.elapsedRealtime())); current = next; remote = null;
        int failures = binding.failures();
        state = failures == 0 ? "正在绑定模块服务（第 " + next.id + " 次）" : refused("模块服务连续 " + failures + " 次未响应绑定，正在重试"); log.accept("BRIDGE " + state);
        if (failures >= 2) bindRefused = true;
        try {
            // Acquire a replacement binding before releasing the old one to avoid a needless Service.onDestroy.
            ComponentName target = new ComponentName(Protocol.MODULE, SERVICE_CLASS);
            log.accept("BRIDGE target=" + target.flattenToShortString());
            next.registered = context.bindService(new Intent().setComponent(target),
                    next, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
            if (!next.registered) { bindRefused = true; state = refused("系统未接受服务绑定，将自动重试"); log.accept("BRIDGE bindService=false"); }
        } catch (RuntimeException e) { state = "服务绑定失败：" + Ipc.error(e); log.accept("BRIDGE " + state); }
        if (previous != null) previous.release();
    }
    private final class Link implements ServiceConnection {
        final long id;
        boolean registered;
        IBinder binder;
        IBinder.DeathRecipient death;
        Link(long id) { this.id = id; }
        @Override public void onServiceConnected(ComponentName name, IBinder value) {
            if (this != current || !binding.current(id)) { release(); return; }
            unlink();
            try {
                death = () -> main.post(() -> lost(value, "模块 Binder 死亡"));
                value.linkToDeath(death, 0); binder = value;
                if (!value.isBinderAlive()) throw new DeadObjectException();
                if (!binding.connected(id)) { unlink(); return; }
                remote = value; bindRefused = false; state = "模块服务已连接"; log.accept("BRIDGE connected generation=" + id);
            } catch (RemoteException e) {
                unlink(); remote = null; binding.disconnected(id, SystemClock.elapsedRealtime());
                state = "连接回调中的 Binder 已失效"; log.accept("BRIDGE " + state);
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) { dropped("onServiceDisconnected"); }
        @Override public void onBindingDied(ComponentName name) { dropped("onBindingDied"); }
        @Override public void onNullBinding(ComponentName name) { dropped("onNullBinding"); }
        private void dropped(String reason) {
            if (this != current || !binding.disconnected(id, SystemClock.elapsedRealtime())) return;
            remote = null; unlink(); state = reason + "，等待重新绑定"; log.accept("BRIDGE " + state);
        }
        void unlink() {
            if (binder != null && death != null) try { binder.unlinkToDeath(death, 0); } catch (RuntimeException ignored) {}
            binder = null; death = null;
        }
        void release() {
            unlink();
            // Android may retain tracking even for null/failed bindings. unbind is best effort in both cases.
            try { context.unbindService(this); } catch (RuntimeException ignored) {}
            registered = false;
        }
    }
}
