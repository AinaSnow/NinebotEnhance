package dev.ichinomiya.ninebotenhance.ui;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;

/** Never previews the live recording inside the recorded app: full-screen capture would feed back. */
final class RecordingPanel {
    private final Activity activity;
    private final ViewGroup host;
    private final LinearLayout content;
    private final PreviewSystemBars bars;
    RecordingPanel(Activity activity, FrameClient frames, Runnable stop) {
        this.activity = activity; host = activity.findViewById(android.R.id.content);
        MirrorUi theme = new MirrorUi(activity, host); int pad = MirrorUi.dp(activity, 24);
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        content.setTag(MirrorUi.PREVIEW_TAG); content.setForceDarkAllowed(false); content.setClickable(true);
        content.setGravity(Gravity.CENTER); content.setPadding(pad, pad, pad, pad); content.setElevation(MirrorUi.dp(activity, 32));
        TextView title = new TextView(activity); title.setText("正在录屏投屏"); title.setTextSize(22); title.setGravity(Gravity.CENTER);
        TextView description = new TextView(activity); description.setText("请切换到要显示的应用并直接在手机上操作。\n可从系统录屏指示或通知结束投屏。");
        description.setTextSize(15); description.setGravity(Gravity.CENTER); description.setPadding(0, pad, 0, pad);
        Button end = new Button(activity); end.setText("结束投屏"); theme.button(end, null); end.setOnClickListener(v -> stop.run());
        content.addView(title, new LinearLayout.LayoutParams(-1, -2)); content.addView(description, new LinearLayout.LayoutParams(-1, -2));
        content.addView(end, new LinearLayout.LayoutParams(-1, -2)); host.addView(content, new ViewGroup.LayoutParams(-1, -1));
        try { bars = new PreviewSystemBars(activity, activity.getWindow(), host, next -> {
            content.setBackgroundColor(next.surface); title.setTextColor(next.text); description.setTextColor(next.secondary); next.button(end, null);
        }, frames); } catch (RuntimeException e) { host.removeView(content); throw e; }
    }
    boolean owns(Activity owner) { return activity == owner; }
    void resume() { bars.resume(); }
    void close() { bars.close(); host.removeView(content); }
}
