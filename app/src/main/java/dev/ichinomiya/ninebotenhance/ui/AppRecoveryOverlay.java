package dev.ichinomiya.ninebotenhance.ui;

import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.AppRecoveryState;

import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Recovery control inside the existing phone preview, never a launcher or a new display/Activity. */
public final class AppRecoveryOverlay extends LinearLayout {
    private final FrameClient frames;
    private final String request;
    private final Runnable cancelTouch;
    private final TextView message;
    private final Button restart;
    private boolean paused = true;
    private long pendingUntil;
    public AppRecoveryOverlay(Context context, FrameClient frames, String request, Runnable cancelTouch) {
        super(context); this.frames = frames; this.request = request; this.cancelTouch = cancelTouch;
        setOrientation(VERTICAL); setGravity(Gravity.CENTER); setBackgroundColor(Color.BLACK); setClickable(true);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
        message = new TextView(context); message.setTextColor(Color.LTGRAY); message.setGravity(Gravity.CENTER);
        message.setMaxLines(3); message.setEllipsize(android.text.TextUtils.TruncateAt.END);
        message.setTextSize(14); message.setPadding(0, 0, 0, pad / 2); addView(message, new LayoutParams(-1, -2));
        restart = new Button(context); restart.setText("重新启动APP"); restart.setAllCaps(false);
        new MirrorUi(context, message).button(restart, "cast");
        addView(restart, new LayoutParams(-2, -2)); setVisibility(GONE);
        restart.setOnClickListener(v -> {
            if (paused || frames.appRecoveryFor(request) != AppRecoveryState.MISSING || SystemClock.elapsedRealtime() < pendingUntil) return;
            cancelTouch.run(); pendingUntil = SystemClock.elapsedRealtime() + 2000;
            frames.restartApp(request); refresh();
        });
    }
    private void refresh() {
        int state = frames.appRecoveryFor(request);
        if (state == AppRecoveryState.HIDDEN) { setVisibility(GONE); pendingUntil = 0; return; }
        if (getVisibility() != VISIBLE) { cancelTouch.run(); setVisibility(VISIBLE); }
        boolean pending = state == AppRecoveryState.RESTARTING || SystemClock.elapsedRealtime() < pendingUntil;
        restart.setEnabled(!pending); restart.setText(pending ? "正在重新启动…" : "重新启动APP");
        String detail = frames.appRecoveryDetail();
        message.setText(pending ? "正在将应用打开到虚拟屏" : detail.isEmpty() ? "应用已离开虚拟屏或已退出" : detail);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (paused || !isAttachedToWindow()) return;
            refresh(); postDelayed(this, 300);
        }
    };
    public void resume() { paused = false; removeCallbacks(tick); post(tick); }
    public void pause() { paused = true; removeCallbacks(tick); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (!paused) { removeCallbacks(tick); post(tick); } }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
}
