package dev.ichinomiya.ninebotenhance.ui;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import dev.ichinomiya.ninebotenhance.ipc.Ipc;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;
import dev.ichinomiya.ninebotenhance.service.ProjectionSession;
import dev.ichinomiya.ninebotenhance.service.ScreenCaptureService;

/** Private, session-bound trampoline. The system owns the app/full-screen picker and consent. */
public final class ScreenCaptureConsentActivity extends Activity {
    private static final int CAPTURE = 401;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ProjectionSession session;
    private String request, token;
    private boolean launched, handedOff, valid;
    private final Runnable watch = new Runnable() { @Override public void run() {
        if (!session.owns(request)) {
            if (launched && !handedOff) try { finishActivity(CAPTURE); } catch (RuntimeException ignored) {}
            finish(); return;
        }
        main.postDelayed(this, 500);
    }};
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        session = ProjectionSession.get(this);
        request = getIntent().getStringExtra(Protocol.REQUEST); token = getIntent().getStringExtra("consent_token");
        launched = state != null && state.getBoolean("launched");
        handedOff = state != null && state.getBoolean("handed_off");
        if (handedOff || !(valid = session.openConsent(request, token, launched))) { finish(); return; }
        main.post(watch);
        if (!launched) try {
            launched = true;
            startActivityForResult(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent(), CAPTURE);
        } catch (RuntimeException e) { session.stop(request, "无法打开系统录屏授权：" + Ipc.error(e)); finish(); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putBoolean("launched", launched); state.putBoolean("handed_off", handedOff);
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != CAPTURE || handedOff || !valid) return;
        if (result != RESULT_OK || data == null) { session.stop(request, "已取消系统录屏授权"); finish(); return; }
        if (!session.owns(request)) { finish(); return; }
        try {
            startForegroundService(new Intent(this, ScreenCaptureService.class).putExtra(Protocol.REQUEST, request)
                    .putExtra("consent_token", token).putExtra("capture_data", data).putExtra("result_code", result));
            handedOff = true;
        } catch (RuntimeException e) { session.stop(request, "无法启动录屏服务：" + Ipc.error(e)); }
        finish();
    }
    @Override protected void onDestroy() {
        main.removeCallbacks(watch);
        if (valid && isFinishing() && !handedOff) session.stop(request, "录屏授权已关闭");
        super.onDestroy();
    }
}
