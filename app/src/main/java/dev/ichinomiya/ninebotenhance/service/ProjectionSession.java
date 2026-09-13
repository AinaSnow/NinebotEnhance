package dev.ichinomiya.ninebotenhance.service;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.Surface;
import dev.ichinomiya.ninebotenhance.core.CaptureSize;
import dev.ichinomiya.ninebotenhance.core.ProjectionGrant;
import dev.ichinomiya.ninebotenhance.core.SessionLease;
import dev.ichinomiya.ninebotenhance.diagnostics.Diagnostics;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;
import dev.ichinomiya.ninebotenhance.ui.ScreenCaptureConsentActivity;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Module-owned recording lease. Tokens and pixels never appear in diagnostics. */
public final class ProjectionSession {
    private static ProjectionSession instance;
    public static synchronized ProjectionSession get(Context context) {
        if (instance == null) instance = new ProjectionSession(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SessionLease lease = new SessionLease();
    private ProjectionGrant grant;
    private String token, lastRequest, state = "录屏未启动";
    private IBinder owner;
    private IBinder.DeathRecipient ownerDeath;
    private int ownerUid, displayId = -1, revision;
    private Surface surface;
    private CaptureSize desired;
    private PendingIntent consent;
    private ScreenCaptureService engine;
    private ProjectionSession(Context context) { this.context = context; }

    public synchronized PendingIntent begin(String request, Surface output, IBinder client, int uid, CaptureSize size) throws RemoteException {
        if (!Protocol.validRequest(request) || output == null || !output.isValid() || client == null || !client.isBinderAlive()) {
            if (output != null) output.release(); throw new IllegalArgumentException("录屏接收端未就绪");
        }
        String secret = UUID.randomUUID().toString().replace("-", "");
        if (!lease.begin(request, secret)) { output.release(); throw new IllegalStateException("已有录屏正在运行"); }
        grant = new ProjectionGrant(); token = secret; lastRequest = request;
        surface = output; owner = client; ownerUid = uid; desired = size; revision = 0; displayId = -1;
        state = "等待系统录屏授权，请选择单个应用或整个屏幕";
        ownerDeath = () -> stop(request, "九号进程已退出");
        try {
            client.linkToDeath(ownerDeath, 0);
            Intent intent = new Intent(context, ScreenCaptureConsentActivity.class)
                    .setData(Uri.parse("ninebot-enhance://capture/" + request))
                    .putExtra(Protocol.REQUEST, request).putExtra("consent_token", secret);
            ActivityOptions options = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 35) options.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            consent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE, options.toBundle());
        } catch (RuntimeException | RemoteException e) { stop(request, "无法准备录屏授权"); throw e; }
        main.postDelayed(() -> { synchronized (ProjectionSession.this) { if (!lease.owns(request) || lease.isReady()) return; }
            stop(request, "录屏授权或启动超时，请重新开始投屏"); }, 180000);
        Diagnostics.add("PROJECTION waiting for user choice; no Root/Shizuku launch");
        return consent;
    }
    public synchronized boolean active() { return lease.request() != null; }
    public synchronized boolean owns(String request) { return lease.owns(request); }
    private boolean matches(String request, String secret) { return lease.owns(request) && token != null && token.equals(secret); }
    public synchronized boolean openConsent(String request, String secret, boolean recreation) {
        if (!matches(request, secret) || !grant.open(recreation)) return false;
        return recreation ? lease.authorize(secret) : lease.attach(secret);
    }
    public record Boot(Surface surface, CaptureSize size, int dpi) {}
    public synchronized Boot consumeGrant(String request, String secret) {
        if (!matches(request, secret) || !lease.authorize(secret) || !grant.consume()) return null;
        state = "正在启动系统录屏";
        return new Boot(surface, desired, context.getResources().getConfiguration().densityDpi);
    }
    public synchronized boolean ready(String request, String secret, ScreenCaptureService service, int id) {
        if (!matches(request, secret) || !grant.ready() || !lease.ready(secret)) return false;
        engine = service; displayId = id; state = "录屏投屏中，可在手机上操作所选应用或整个屏幕";
        Diagnostics.add("PROJECTION ready " + desired.width() + "x" + desired.height());
        return true;
    }
    public synchronized void contentSize(String request, int width, int height) {
        if (!lease.owns(request)) return;
        CaptureSize next = CaptureSize.fit(width, height);
        if (next.equals(desired)) return;
        desired = next; revision++;
        Diagnostics.add("PROJECTION content " + width + "x" + height + " surface=" + next.width() + "x" + next.height());
    }
    /** The new Surface is adopted only after the existing VirtualDisplay has resized successfully. */
    public boolean replaceSurface(String request, int uid, Surface output, CaptureSize size, int version) throws Exception {
        if (output == null || !output.isValid()) { if (output != null) output.release(); throw new IllegalArgumentException("无效的录屏接收面"); }
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        main.post(() -> {
            synchronized (ProjectionSession.this) {
                if (result.isDone() || !lease.owns(request) || !lease.isReady() || uid != ownerUid
                        || revision != version || !size.equals(desired) || engine == null) {
                    output.release(); result.complete(false); return;
                }
                try {
                    engine.resizeOutput(output, size);
                    Surface previous = surface; surface = output;
                    if (previous != null) previous.release();
                    result.complete(true);
                } catch (RuntimeException e) { output.release(); result.completeExceptionally(e); stop(request, "录屏尺寸更新失败"); }
            }
        });
        try { return result.get(3, TimeUnit.SECONDS); }
        catch (Exception e) { result.cancel(false); stop(request, "录屏尺寸更新失败或超时"); throw e; }
    }
    public synchronized Bundle status() {
        Bundle result = new Bundle(); result.putString(Protocol.REQUEST, lease.request() == null ? lastRequest : lease.request());
        result.putInt("brokerPid", android.os.Process.myPid()); result.putString("brokerVersion", Protocol.VERSION);
        result.putBoolean("active", active()); result.putBoolean("ready", lease.isReady());
        result.putBoolean(Protocol.SCREEN_CAPTURE, true); result.putString("backend", "系统录屏，无 Root / Shizuku 权限");
        result.putString("state", state); result.putInt("displayId", displayId);
        if (desired != null) { result.putInt(Protocol.CAPTURE_WIDTH, desired.width()); result.putInt(Protocol.CAPTURE_HEIGHT, desired.height()); }
        result.putInt(Protocol.CAPTURE_REVISION, revision); return result;
    }
    public void stopCurrent(String reason) { String request; synchronized (this) { request = lease.request(); } stop(request, reason); }
    public void stop(String request, String reason) {
        ScreenCaptureService previous; Surface output;
        synchronized (this) {
            if (!lease.end(request)) return;
            grant.stop(); token = null; state = reason; displayId = -1;
            if (consent != null) consent.cancel(); consent = null;
            if (owner != null && ownerDeath != null) try { owner.unlinkToDeath(ownerDeath, 0); } catch (RuntimeException ignored) {}
            owner = null; ownerDeath = null; previous = engine; engine = null; output = surface; surface = null;
        }
        main.post(() -> { if (previous != null) previous.end(request); if (output != null) output.release(); });
        Diagnostics.add("PROJECTION stopped: " + reason);
    }
}
