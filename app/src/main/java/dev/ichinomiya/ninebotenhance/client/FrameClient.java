package dev.ichinomiya.ninebotenhance.client;

import dev.ichinomiya.ninebotenhance.core.AppRecoveryState;
import dev.ichinomiya.ninebotenhance.core.DirectSession;
import dev.ichinomiya.ninebotenhance.core.DisplaySettings;
import dev.ichinomiya.ninebotenhance.core.FramePacer;
import dev.ichinomiya.ninebotenhance.core.CaptureSize;
import dev.ichinomiya.ninebotenhance.core.PrivilegeMode;
import dev.ichinomiya.ninebotenhance.core.Geometry;
import dev.ichinomiya.ninebotenhance.core.KeyboardPolicy;
import dev.ichinomiya.ninebotenhance.core.PendingStops;
import dev.ichinomiya.ninebotenhance.core.PixelPacking;
import dev.ichinomiya.ninebotenhance.diagnostics.Diagnostics;
import dev.ichinomiya.ninebotenhance.diagnostics.LogDigest;
import dev.ichinomiya.ninebotenhance.ipc.Ipc;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;
import dev.ichinomiya.ninebotenhance.platform.AppCatalog;
import dev.ichinomiya.ninebotenhance.client.AppIconLoader;

import android.content.*;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.Surface;
import android.view.View;
import android.view.MotionEvent;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.ichinomiya.ninebotenhance.diagnostics.StreamStats;
import dev.ichinomiya.ninebotenhance.diagnostics.EncodingDiagnostics;

/** Ninebot owns the consumer Surface: compositor -> RGBA -> original encoder. No JPEG IPC. */
public final class FrameClient {
    private final Handler main = new Handler(Looper.getMainLooper()), worker, controlWorker, metadataWorker;
    private final ServiceBridge bridge = new ServiceBridge(this::report);
    private final LogExporter logExporter = new LogExporter(bridge);
    private final AppIconLoader appIcons = new AppIconLoader(bridge);
    private final PendingStops pendingStops = new PendingStops();
    private final AtomicBoolean metadataBusy = new AtomicBoolean();
    private final AtomicInteger pendingInput = new AtomicInteger();
    private volatile WeakReference<View> inlinePreview = new WeakReference<>(null);
    private long lastControlError;
    private final Object frameLock = new Object();
    private final ArrayDeque<String> reports = new ArrayDeque<>();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Set<Bitmap> supplied = Collections.newSetFromMap(new WeakHashMap<>());
    private final String process;
    private Context context;
    private volatile String beginAccepted;
    private volatile String moduleDigest = "尚未取得模块进程日志", brokerIdentity = "尚未取得模块进程标识";
    private volatile DisplaySettings savedSettings = DisplaySettings.defaults();
    private volatile String savedApp = "";
    private volatile PrivilegeMode savedPrivilege = PrivilegeMode.AUTO;
    private volatile boolean screenCapture;
    private volatile int appRecovery;
    private volatile String appRecoveryDetail = "";
    private long lastDiagnostics;
    private volatile String ownerRequest, state = "等待模块连接", summary = "等待业务类加载";
    private volatile String brokerStatus = "尚未取得模块会话状态";
    private volatile boolean active, displayReady;
    private volatile boolean previewRotated;
    private volatile DirectSession.Mode mode;
    private volatile long lastPoll;
    private volatile DisplaySettings settings = DisplaySettings.defaults();
    private ImageReader reader;
    private final FramePacer framePacer = new FramePacer(); // Accessed only on the frame worker.
    private Runnable pendingImageRead;
    private ByteBuffer packed;
    private volatile Bitmap latest;
    private volatile long lastImage, replacements, earlyFrames, deduplicated;
    private long lastStats;
    private volatile StreamStats streamStats;
    private final EncodingDiagnostics encoding = new EncodingDiagnostics(this::report);
    private volatile String observedRequest;
    private final Object observationLock = new Object();
    private final IBinder owner = new Binder(); // Liveness only; UI frames remain inside Ninebot.
    public FrameClient(String process) {
        this.process = process;
        HandlerThread thread = new HandlerThread("Ninebot-VirtualFrames"); thread.start(); worker = new Handler(thread.getLooper());
        HandlerThread controls = new HandlerThread("Ninebot-VirtualInput"); controls.start(); controlWorker = new Handler(controls.getLooper());
        HandlerThread metadata = new HandlerThread("Ninebot-Metadata"); metadata.start(); metadataWorker = new Handler(metadata.getLooper());
    }
    public void attach(Context app) {
        main.post(() -> {
            if (context != null) return;
            context = app.getApplicationContext() != null ? app.getApplicationContext() : app;
            try {
                SharedPreferences p = context.getSharedPreferences("dev.ichinomiya.ninebotenhance.cached_display", Context.MODE_PRIVATE);
                savedSettings = DisplaySettings.read(p::getInt);
                savedApp = p.getString(AppCatalog.SELECTED, "");
                savedPrivilege = PrivilegeMode.parse(p.getString("privilege_mode", "AUTO"));
            } catch (RuntimeException e) { report("SETTINGS cache " + Ipc.error(e)); }
            bridge.attach(context); worker.post(poll); controlWorker.post(stopRetry);
        });
    }
    public EncodingDiagnostics encoding() { return encoding; }
    public void beginSessionObservations(String request, DirectSession.Mode mode) {
        synchronized (observationLock) {
            if (request.equals(observedRequest)) return;
            observedRequest = request;
            streamStats = new StreamStats(SystemClock.elapsedRealtime());
            encoding.begin(request, mode == DirectSession.Mode.VEHICLE, SystemClock.elapsedRealtime());
        }
    }
    public void startDirect(String request, DirectSession.Mode mode, Consumer<String> done) {
        startDirect(request, mode, null, done);
    }
    public void startDirect(String request, DirectSession.Mode mode, Activity activity, Consumer<String> done) {
        beginSessionObservations(request, mode);
        this.mode = mode;
        ownerRequest = request; active = true; displayReady = false; previewRotated = false; state = "正在连接虚拟屏";
        appRecovery = AppRecoveryState.HIDDEN; appRecoveryDetail = "";
        beginAccepted = null; lastPoll = 0; bridge.ensure();
        AtomicBoolean completed = new AtomicBoolean();
        main.postDelayed(() -> {
            if (completed.compareAndSet(false, true) && request.equals(ownerRequest)) {
                report("VD BEGIN timed out before acknowledgement"); stopDirect(request);
                done.accept("模块服务连接或启动请求超时，请在设置中查看日志");
            }
        }, 15000);
        worker.post(new Runnable() {
            @Override public void run() {
                if (completed.get() || !request.equals(ownerRequest)) return;
                if (!bridge.connected() || pendingStops.size() > 0) {
                    bridge.ensure(); controlWorker.post(FrameClient.this::flushStops);
                    state = "正在等待模块服务连接及上一会话清理"; worker.postDelayed(this, 150); return;
                }
                try {
                    Bundle config = bridge.call(Protocol.SETTINGS, new Bundle());
                    DisplaySettings value = Ipc.settings(config); String selected = config.getString(AppCatalog.SELECTED, "");
                    savedPrivilege = PrivilegeMode.parse(config.getString("privilege_mode", "AUTO"));
                    screenCapture = !savedPrivilege.usesVirtualDisplay();
                    if (screenCapture && (mode != DirectSession.Mode.VEHICLE || activity == null))
                        throw new IllegalArgumentException("录屏模式只用于车辆投屏");
                    if (!screenCapture && selected.isEmpty()) throw new IllegalArgumentException("请先在设置中选择启动应用并保存");
                    if (completed.get() || !request.equals(ownerRequest)) return;
                    settings = value; cacheSettings(value, selected); closeFrames();
                    int width = settings.width, height = settings.height;
                    if (screenCapture) {
                        Rect bounds = activity.getSystemService(android.view.WindowManager.class).getMaximumWindowMetrics().getBounds();
                        CaptureSize capture = CaptureSize.fit(bounds.width(), bounds.height()); width = capture.width(); height = capture.height();
                        state = "正在准备系统录屏授权";
                    }
                    reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
                    packed = ByteBuffer.allocateDirect(width * (height + (screenCapture ? 0 : settings.topInset)) * 4);
                    ImageReader current = reader; current.setOnImageAvailableListener(source -> scheduleImage(request, source), worker);
                    Surface surface = current.getSurface();
                    try { surface.setFrameRate(FramePacer.TARGET_FPS, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT); }
                    catch (RuntimeException e) { report("RGBA surface frame-rate hint unavailable " + Ipc.error(e)); }
                    report("RGBA pacing targetFps=" + FramePacer.TARGET_FPS + " intervalMs=" + FramePacer.INTERVAL_MS + " policy=defer-latest");
                    if (completed.get() || !request.equals(ownerRequest)) { closeFrames(); return; }
                    Bundle args = Ipc.request(request); Ipc.settings(args, settings); args.putParcelable("surface", surface); args.putBinder("owner", owner);
                    args.putString(AppCatalog.SELECTED, selected);
                    args.putBoolean(Protocol.SCREEN_CAPTURE, screenCapture); args.putBoolean("local", mode == DirectSession.Mode.LOCAL);
                    args.putInt(Protocol.CAPTURE_WIDTH, width); args.putInt(Protocol.CAPTURE_HEIGHT, height);
                    Bundle status = bridge.call(Protocol.BEGIN, args);
                    if (completed.get() || !request.equals(ownerRequest)) { stopDirect(request); return; }
                    beginAccepted = request; acceptStatus(request, status);
                    PendingIntent consent = status.getParcelable(Protocol.CAPTURE_CONSENT, PendingIntent.class);
                    main.post(() -> {
                        if (!completed.compareAndSet(false, true) || !request.equals(ownerRequest)) return;
                        try {
                            if (screenCapture) {
                                if (consent == null || activity == null || activity.isFinishing() || activity.isDestroyed())
                                    throw new IllegalStateException("录屏授权入口已失效，请重新开始投屏");
                                ActivityOptions options = ActivityOptions.makeBasic()
                                        .setPendingIntentBackgroundActivityStartMode(Build.VERSION.SDK_INT >= 36
                                                ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                                activity.startIntentSenderForResult(consent.getIntentSender(), -1, null, 0, 0, 0, options.toBundle());
                            }
                            done.accept(null);
                        } catch (Exception e) { stopDirect(request); done.accept(Ipc.error(e)); }
                    });
                } catch (Exception e) {
                    String error = Ipc.error(e); report("VD BEGIN " + error); stopDirect(request);
                    if (completed.compareAndSet(false, true)) main.post(() -> done.accept(error));
                }
            }
        });
    }
    private void scheduleImage(String request, ImageReader source) {
        if (source != reader || !request.equals(ownerRequest)) return;
        FramePacer.Ticket ticket = framePacer.schedule(SystemClock.elapsedRealtime());
        if (ticket == null) return; // Coalesce callbacks into one pending read; retain no acquired Image.
        Runnable read = () -> {
            if (!framePacer.dispatch(ticket)) return; // Closed/replaced reader or cancelled session.
            pendingImageRead = null;
            readImage(request, source);
        };
        pendingImageRead = read;
        if (ticket.delayMs == 0) read.run();
        else worker.postDelayed(read, ticket.delayMs);
    }
    private void readImage(String request, ImageReader source) {
        if (source != reader || !request.equals(ownerRequest)) return;
        StreamStats samples = streamStats;
        try (Image image = source.acquireLatestImage()) {
            if (image == null || source != reader || !request.equals(ownerRequest)) return;
            long now = SystemClock.elapsedRealtime();
            Image.Plane plane = image.getPlanes()[0];
            int width = source.getWidth(), height = source.getHeight(), inset = screenCapture ? 0 : settings.topInset;
            PixelPacking.rgba(plane.getBuffer(), plane.getRowStride(), plane.getPixelStride(), width, height, packed, inset, settings.topColor);
            Bitmap bitmap = Bitmap.createBitmap(width, height + inset, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(packed); bitmap.setDensity(Bitmap.DENSITY_NONE);
            if (lastImage == 0) report("RGBA first frame " + width + "x" + (height + inset) + " source=" + (screenCapture ? "MediaProjection" : "VirtualDisplay"));
            synchronized (frameLock) { latest = bitmap; lastImage = now; }
            framePacer.captured(now);
            if (samples != null) samples.captured(now);
            View preview = inlinePreview.get(); if (preview != null) preview.postInvalidateOnAnimation();
        } catch (IllegalStateException e) { /* reader closed by cancellation */ }
        catch (RuntimeException e) { report("RGBA " + Ipc.error(e)); }
    }
    public void attachInline(String request, View view) {
        if (!request.equals(ownerRequest)) return;
        inlinePreview = new WeakReference<>(view); view.postInvalidateOnAnimation();
    }
    public void detachInline(View view) { if (inlinePreview.get() == view) inlinePreview.clear(); }
    public DisplaySettings displaySettings() { return settings; }
    public StreamStats.Snapshot statistics() { StreamStats value=streamStats;return value==null?null:value.snapshot(SystemClock.elapsedRealtime()); }
    public StreamStats transportStats() { return ready() && mode==DirectSession.Mode.VEHICLE ? streamStats : null; }
    public boolean previewRotated(String request) { return request.equals(ownerRequest) && previewRotated; }
    public void setPreviewRotated(String request, boolean value) { if (request.equals(ownerRequest)) previewRotated = value; }
    public void drawInline(String request, Canvas canvas, int width, int height, boolean rotated) {
        canvas.drawColor(Color.BLACK);
        // Published frames are never mutated/recycled; the UI must not wait for encoder scaling under frameLock.
        Bitmap snapshot = request.equals(ownerRequest) && ready() ? latest : null;
        if (snapshot == null || width < 1 || height < 1) return;
        int save = canvas.save();
        if (rotated) { canvas.translate(width, 0); canvas.rotate(90); int swap = width; width = height; height = swap; }
        float[] r = Geometry.fit(snapshot.getWidth(), snapshot.getHeight(), width, height);
        canvas.drawBitmap(snapshot, null, new RectF(r[0], r[1], r[2], r[3]), paint);
        canvas.restoreToCount(save);
        // A phone repaint is not an encoder capture: do not advance replacement counters.
    }
    public void back(String request) { control(request, Protocol.UI_BACK, null); }
    public boolean screenCapture() { return screenCapture; }
    public int appRecoveryFor(String request) {
        return captureActiveFor(request) && displayReady ? appRecovery : AppRecoveryState.HIDDEN;
    }
    public String appRecoveryDetail() { return appRecoveryDetail; }
    public void restartApp(String request) {
        if (appRecoveryFor(request) == AppRecoveryState.MISSING) control(request, Protocol.UI_RESTART_APP, null);
    }
    /** Takes ownership of an already mapped event, including on failure or stale-session paths. */
    public void input(String request, MotionEvent event) { control(request, Protocol.UI_INPUT, event); }
    public void text(String request, String value) {
        try { KeyboardPolicy.text(value); } catch (IllegalArgumentException e) { return; }
        if (value.isEmpty()) return;
        Bundle args = new Bundle(); args.putString("text", value); control(request, Protocol.UI_TEXT, null, args);
    }
    public void deleteText(String request, int before, int after) {
        KeyboardPolicy.deletion(before, after); Bundle args = new Bundle(); args.putInt("before", before); args.putInt("after", after);
        control(request, Protocol.UI_DELETE, null, args);
    }
    public void typingKey(String request, android.view.KeyEvent event) {
        if (!KeyboardPolicy.key(event.getKeyCode()) || (event.getAction() != 0 && event.getAction() != 1)) return;
        Bundle args = new Bundle(); args.putInt("key", event.getKeyCode()); args.putInt("action", event.getAction()); args.putInt("meta", event.getMetaState());
        control(request, Protocol.UI_TYPING_KEY, null, args);
    }
    private void control(String request, int code, MotionEvent event) {
        control(request, code, event, new Bundle());
    }
    private void control(String request, int code, MotionEvent event, Bundle payload) {
        if (screenCapture || !request.equals(ownerRequest) || (event != null && event.getActionMasked() == MotionEvent.ACTION_MOVE && pendingInput.get() >= 3)) {
            if (event != null) event.recycle(); return;
        }
        pendingInput.incrementAndGet();
        controlWorker.post(() -> {
            try {
                if (!request.equals(ownerRequest)) return;
                Bundle args = new Bundle(payload); args.putString(Protocol.REQUEST, request); if (event != null) args.putParcelable("event", event);
                bridge.call(code, args);
            } catch (Exception e) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastControlError > 5000) {
                    lastControlError = now; report("INLINE CONTROL " + Ipc.error(e));
                    main.post(() -> { if (context != null && request.equals(ownerRequest))
                        android.widget.Toast.makeText(context, "虚拟屏操作失败，请查看日志", 1).show(); });
                }
            } finally { pendingInput.decrementAndGet(); if (event != null) event.recycle(); }
        });
    }
    private void closeFrames() {
        if (pendingImageRead != null) { worker.removeCallbacks(pendingImageRead); pendingImageRead = null; }
        framePacer.reset();
        if (reader != null) { reader.setOnImageAvailableListener(null, null); reader.close(); reader = null; }
        packed = null;
        synchronized (frameLock) { latest = null; lastImage = 0; supplied.clear(); }
        View preview = inlinePreview.get(); if (preview != null) preview.postInvalidateOnAnimation();
    }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            synchronized (observationLock) { encoding.tick(SystemClock.elapsedRealtime(), statistics()); }
            try {
                if (bridge.connected()) {
                    Bundle status = bridge.call(Protocol.READ, new Bundle());
                    observeBroker(status);
                    String request = ownerRequest;
                    if (request != null && request.equals(beginAccepted)) acceptStatus(request, status);
                    for (int i = 0; i < 8; i++) {
                        String message; synchronized (reports) { message = reports.pollFirst(); } if (message == null) break;
                        sendReport(process + " " + message);
                    }
                    if (active && SystemClock.elapsedRealtime() - lastStats > 3000) {
                        sendReport("STAT " + summary + " replaced=" + replacements + " early=" + earlyFrames + " dedup=" + deduplicated);
                        lastStats = SystemClock.elapsedRealtime();
                    }
                    if (SystemClock.elapsedRealtime() - lastDiagnostics > 10000 && !metadataBusy.get()) {
                        lastDiagnostics = SystemClock.elapsedRealtime(); refreshModuleDiagnostics();
                    }
                } else { bridge.ensure(); if (ownerRequest != null) state = "服务暂时断开，正在自动重连"; }
            } catch (Exception e) { state = "模块连接异常：" + Ipc.error(e); report(state); }
            worker.postDelayed(this, active ? 300 : 1000);
        }
    };
    private void acceptStatus(String request, Bundle status) {
        if (!request.equals(ownerRequest)) return;
        observeBroker(status);
        String observed = "active=" + status.getBoolean("active") + " ready=" + status.getBoolean("ready")
                + " displayId=" + status.getInt("displayId", -1) + " " + status.getString("state", "")
                + " appRecovery=" + status.getInt(Protocol.APP_RECOVERY)
                + " appLayout=" + status.getString(Protocol.APP_LAYOUT_POLICY, "unreported") + " backend=" + status.getString("backend", "unreported");
        if (!observed.equals(brokerStatus)) { brokerStatus = observed; report("VD STATUS " + observed); }
        if (request.equals(status.getString(Protocol.REQUEST))) {
            active = status.getBoolean("active"); displayReady = status.getBoolean("ready"); state = status.getString("state", "");
            appRecovery = status.getInt(Protocol.APP_RECOVERY); appRecoveryDetail = status.getString(Protocol.APP_RECOVERY_DETAIL, "");
        } else {
            active = displayReady = false; state = "模块服务已重启或会话已失效，请重新开始投屏";
            appRecovery = AppRecoveryState.HIDDEN; appRecoveryDetail = "";
            report("BRIDGE requested session absent after reconnect");
        }
        lastPoll = SystemClock.elapsedRealtime();
        if (active && displayReady && screenCapture && reader != null) resizeRecording(request, status);
        if (!active && reader != null) closeFrames();
    }
    private void resizeRecording(String request, Bundle status) {
        int width = status.getInt(Protocol.CAPTURE_WIDTH), height = status.getInt(Protocol.CAPTURE_HEIGHT);
        if (reader.getWidth() == width && reader.getHeight() == height) return;
        ImageReader next = null;
        try {
            CaptureSize size = new CaptureSize(width, height);
            next = ImageReader.newInstance(size.width(), size.height(), PixelFormat.RGBA_8888, 3);
            Bundle args = Ipc.request(request); args.putParcelable("surface", next.getSurface());
            args.putInt(Protocol.CAPTURE_WIDTH, width); args.putInt(Protocol.CAPTURE_HEIGHT, height);
            args.putInt(Protocol.CAPTURE_REVISION, status.getInt(Protocol.CAPTURE_REVISION));
            boolean accepted = bridge.call(Protocol.PROJECTION_SURFACE, args).getBoolean("accepted");
            if (!accepted || !request.equals(ownerRequest)) return;
            closeFrames(); reader = next; next = null;
            packed = ByteBuffer.allocateDirect(width * height * 4);
            reader.setOnImageAvailableListener(source -> scheduleImage(request, source), worker);
            scheduleImage(request, reader);
            report("PROJECTION receiving " + width + "x" + height);
        } catch (Exception e) { report("PROJECTION resize " + Ipc.error(e)); stopDirect(request); }
        finally { if (next != null) next.close(); }
    }
    private void observeBroker(Bundle status) {
        String identity = "pid=" + status.getInt("brokerPid", -1) + " version=" + status.getString("brokerVersion", "旧版未报告");
        if (!identity.equals(brokerIdentity)) {
            brokerIdentity = identity; report("BRIDGE module process " + identity); lastDiagnostics = 0;
        }
    }
    private void sendReport(String message) throws RemoteException {
        Bundle args = new Bundle(); args.putString("message", message); bridge.call(Protocol.REPORT, args);
    }
    public void report(String value) {
        Diagnostics.add(process + " " + value);
        synchronized (reports) { reports.addLast(value); while (reports.size() > 120) reports.removeFirst(); }
    }
    public void summary(String value) { summary = value; }
    public String status() { return bridge.status() + "\n" + state + "\n" + summary; }
    public long replacementCount() { synchronized (frameLock) { return replacements; } }
    public boolean readyFor(String request) { synchronized (frameLock) { return request.equals(ownerRequest) && ready(); } }
    public boolean captureActiveFor(String request) { return request.equals(ownerRequest) && active && SystemClock.elapsedRealtime() - lastPoll < 4000; }
    public boolean failedFor(String request) { return request.equals(ownerRequest) && !active && lastPoll != 0; }
    private boolean ready() { return active && displayReady && latest != null && SystemClock.elapsedRealtime() - lastPoll < 4000; }
    public void stopDirect(String request) {
        synchronized (observationLock) {
            if (request != null && request.equals(observedRequest)) {
                encoding.stop(request, SystemClock.elapsedRealtime(), statistics());
                if (streamStats != null) streamStats.stop(SystemClock.elapsedRealtime());
            }
        }
        if (request == null) return;
        boolean owns = request.equals(ownerRequest);
        if (owns) { ownerRequest = null; beginAccepted = null; active = displayReady = false; mode = null; }
        pendingStops.add(request); bridge.ensure(); controlWorker.post(this::flushStops);
        worker.post(() -> { if (owns && ownerRequest == null) closeFrames(); });
    }
    private final Runnable stopRetry = new Runnable() {
        @Override public void run() { flushStops(); controlWorker.postDelayed(this, 1000); }
    };
    private void flushStops() {
        if (!bridge.connected()) { if (pendingStops.size() > 0) bridge.ensure(); return; }
        for (int count = 0; count < 8; count++) {
            PendingStops.Entry stop = pendingStops.first(); if (stop == null) return;
            try { bridge.call(Protocol.STOP_DIRECT, Ipc.request(stop.request)); pendingStops.acknowledged(stop); report("STOP acknowledged"); }
            catch (Exception e) { report("STOP pending retry " + Ipc.error(e)); return; }
        }
    }
    public boolean draw(Canvas canvas, int width, int height) {
        synchronized (frameLock) {
            if (mode != DirectSession.Mode.VEHICLE || !ready() || width < 1 || height < 1 || (long)width * height > 4096L * 2160) return false;
            float[] r = Geometry.fit(latest.getWidth(), latest.getHeight(), width, height); int saved = canvas.save();
            try { canvas.clipRect(0, 0, width, height); canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(latest, null, new RectF(r[0], r[1], r[2], r[3]), paint);
            } finally { canvas.restoreToCount(saved); }
            replacements++; if (streamStats != null) streamStats.replaced(); return true;
        }
    }
    public Bitmap replacement(int width, int height, int density, boolean early) {
        synchronized (frameLock) {
            if (mode != DirectSession.Mode.VEHICLE || !ready() || width <= 0 || height <= 0 || (long)width * height > 4096L * 2160) return null;
            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); output.setDensity(density);
            if (!draw(new Canvas(output), width, height)) { output.recycle(); return null; }
            supplied.add(output); if (early) earlyFrames++;
            return output; // Encoder owns this object; never recycle or overwrite it from the producer.
        }
    }
    public Bitmap replace(Bitmap original) {
        synchronized (frameLock) {
            if (mode != DirectSession.Mode.VEHICLE || original == null || original.isRecycled() || !ready()) return null;
            if (supplied.contains(original)) { deduplicated++; return original; }
            return replacement(original.getWidth(), original.getHeight(), original.getDensity(), false);
        }
    }
    public DisplaySettings cachedSettings() { return savedSettings; }
    public PrivilegeMode cachedPrivilege() { return savedPrivilege; }
    public void appIcon(String component, android.widget.ImageView target) { appIcons.load(component, target); }
    private void cacheSettings(DisplaySettings value, String selected) {
        savedSettings = value; savedApp = selected;
        try {
            if (context != null) context.getSharedPreferences("dev.ichinomiya.ninebotenhance.cached_display", Context.MODE_PRIVATE).edit()
                    .putInt("width", value.width).putInt("height", value.height).putInt("dpi", value.dpi).putInt("top_inset", value.topInset).putInt("top_color", value.topColor)
                    .putString(AppCatalog.SELECTED, selected).apply();
        } catch (RuntimeException e) { report("SETTINGS cache write " + Ipc.error(e)); }
    }
    /** One bounded worker for settings/log RPC. A stuck transact cannot block the UI's timeout or local log viewing. */
    private void metadataCall(int code, Bundle args, Consumer<Bundle> done, Consumer<String> failed) {
        if (!metadataBusy.compareAndSet(false, true)) {
            main.post(() -> failed.accept("上一条设置或日志请求尚未返回，可先查看本地日志，再稍后重新读取")); return;
        }
        AtomicBoolean completed = new AtomicBoolean(); bridge.ensure();
        Runnable timeout = () -> {
            if (completed.compareAndSet(false, true)) {
                report("METADATA timeout code=" + code);
                failed.accept("服务响应超时，结果尚未确认；请重连后重新读取核对");
            }
        };
        main.postDelayed(timeout, 3500);
        metadataWorker.post(new Runnable() {
            @Override public void run() {
                if (completed.get()) { metadataBusy.set(false); return; }
                if (!bridge.connected()) { bridge.ensure(); metadataWorker.postDelayed(this, 150); return; }
                Bundle response = null; String failure = null;
                try { response = bridge.call(code, args); }
                catch (Exception e) { failure = Ipc.error(e); report("METADATA code=" + code + " " + failure); }
                finally { metadataBusy.set(false); }
                // Release the slot before invoking UI callbacks, which may immediately read after saving a mode.
                Bundle result = response; String error = failure;
                main.post(() -> {
                    if (!completed.compareAndSet(false, true)) return;
                    main.removeCallbacks(timeout);
                    if (error == null) done.accept(result); else failed.accept(error);
                });
            }
        });
    }
    public void getSettings(Consumer<Bundle> done, Consumer<String> failed) {
        Bundle args = new Bundle(); args.putBoolean("include_apps", true);
        metadataCall(Protocol.SETTINGS, args, result -> {
            try { cachePrivilege(result); cacheSettings(Ipc.settings(result), result.getString(AppCatalog.SELECTED, "")); done.accept(result); }
            catch (RuntimeException e) { failed.accept(Ipc.error(e)); }
        }, failed);
    }
    private void cachePrivilege(Bundle result) {
        savedPrivilege = PrivilegeMode.parse(result.getString("privilege_mode", "AUTO"));
        if (context != null) context.getSharedPreferences("dev.ichinomiya.ninebotenhance.cached_display", Context.MODE_PRIVATE)
                .edit().putString("privilege_mode", savedPrivilege.name()).apply();
    }
    public void privilege(Bundle args, Consumer<Bundle> done, Consumer<String> failed) {
        metadataCall(Protocol.PRIVILEGE, args, result -> {
            try { cachePrivilege(result); done.accept(result); } catch (RuntimeException e) { failed.accept(Ipc.error(e)); }
        }, failed);
    }
    public void saveSettings(DisplaySettings value, String selected, Consumer<String> done) {
        Bundle args = new Bundle(); args.putBoolean("save", true); Ipc.settings(args, value);
        args.putString(AppCatalog.SELECTED, selected);
        metadataCall(Protocol.SETTINGS, args, result -> {
            try { cacheSettings(Ipc.settings(result), result.getString(AppCatalog.SELECTED, "")); done.accept(null); }
            catch (RuntimeException e) { done.accept(Ipc.error(e)); }
        }, done);
    }
    private void refreshModuleDiagnostics() {
        metadataCall(Protocol.LOG, new Bundle(), result -> {
            String collected = "采集时间：" + new Date() + "\n";
            String full = result.getString("text", "无模块日志");
            moduleDigest = collected + result.getString("compact", LogDigest.recent(full, 1000));
        }, error -> {});
    }
    private String diagnosticHeader() {
        return "Ninebot Enhance " + Protocol.VERSION + " / Android " + Build.VERSION.RELEASE + " / " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n当前连接：\n" + status() + "\nprocess=" + process + " pid=" + android.os.Process.myPid()
                + " mode=" + mode + " captureSource=" + (screenCapture ? "MediaProjection" : "VirtualDisplay")
                + " selectedApp=" + savedApp + " pendingStops=" + pendingStops.size() + " metadataBusy=" + metadataBusy.get()
                + "\n模块进程：" + brokerIdentity + "\n最后模块会话状态：" + brokerStatus
                + "\nreplaced=" + replacements + " early=" + earlyFrames + " dedup=" + deduplicated
                + " statusAgeMs=" + (lastPoll == 0 ? -1 : SystemClock.elapsedRealtime() - lastPoll)
                + " frameAgeMs=" + (lastImage == 0 ? -1 : SystemClock.elapsedRealtime() - lastImage);
    }
    public void diagnostics(Consumer<String> done) {
        // Never wait for the frame/control/metadata workers: all three may be waiting on another process.
        main.post(() -> done.accept(LogDigest.head(diagnosticHeader(), 900)
                + "\n\n原会话编码配置：\n" + encoding.summary()
                + "\n\n最近本地事件（新到旧，已略去 Hook 签名）：\n" + Diagnostics.recent(1500)
                + "\n\n模块缓存（可能早于当前故障）：\n" + LogDigest.head(moduleDigest, 1000)
                + "\n\n点击“分享完整日志”导出当前保留的日志文件。"));
    }
    public void shareFullLog(Consumer<android.net.Uri> done, Consumer<String> failed) {
        logExporter.export(() -> diagnosticHeader()
                + "\n\n原会话编码配置：\n" + encoding.details()
                + "\n\n九号进程本地日志：\n" + Diagnostics.text(), done, failed);
    }
}
