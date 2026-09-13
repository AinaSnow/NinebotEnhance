package dev.ichinomiya.ninebotenhance.ui;

import dev.ichinomiya.ninebotenhance.client.FrameClient;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Icon + two separate text lines, both in the selected row and in the settings chooser. */
public final class AppPickerAdapter extends BaseAdapter {
    private final Context context;
    private final FrameClient frames;
    private final MirrorUi theme;
    private final ArrayList<Bundle> apps;
    private boolean loading = true;
    public AppPickerAdapter(Context context, FrameClient frames, MirrorUi theme, ArrayList<Bundle> apps) {
        this.context = context; this.frames = frames; this.theme = theme; this.apps = apps;
    }
    public void loaded() { loading = false; notifyDataSetChanged(); }
    @Override public int getCount() { return Math.max(1, apps.size()); }
    @Override public Bundle getItem(int position) { return position < apps.size() ? apps.get(position) : null; }
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convert, ViewGroup parent) { return row(position, convert, true); }
    @Override public View getDropDownView(int position, View convert, ViewGroup parent) { return row(position, convert, false); }
    private View row(int position, View convert, boolean selected) {
        Row row = convert instanceof Row ? (Row)convert : new Row();
        Bundle app = getItem(position);
        row.name.setText(app == null ? loading ? "正在读取应用列表…" : "请选择启动应用" : app.getString("label"));
        row.pkg.setText(app == null ? "用于仪表投屏和本地预览" : app.getString("package"));
        row.icon.setTag(null); row.icon.setImageDrawable(new MirrorUi.Glyph("app", theme.secondary));
        row.chevron.setVisibility(selected ? View.VISIBLE : View.GONE);
        row.setBackgroundColor(selected ? theme.input : android.graphics.Color.TRANSPARENT);
        if (app != null) frames.appIcon(app.getString("component"), row.icon);
        return row;
    }
    private final class Row extends LinearLayout {
        final ImageView icon, chevron;
        final TextView name, pkg;
        Row() {
            super(context); setGravity(Gravity.CENTER_VERTICAL); setOrientation(HORIZONTAL);
            int pad = MirrorUi.dp(context, 12); setPadding(pad, pad, pad, pad); setMinimumHeight(MirrorUi.dp(context, 68));
            setBackgroundColor(theme.input);
            icon = new ImageView(context); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams iconParams = new LayoutParams(MirrorUi.dp(context, 40), MirrorUi.dp(context, 40)); iconParams.setMarginEnd(pad); addView(icon, iconParams);
            LinearLayout labels = new LinearLayout(context); labels.setOrientation(VERTICAL);
            name = new TextView(context); name.setTextSize(16); name.setTextColor(theme.text); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            pkg = new TextView(context); pkg.setTextSize(12); pkg.setTextColor(theme.secondary); pkg.setSingleLine(true); pkg.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            labels.addView(name, new LayoutParams(-1, -2));
            LayoutParams subtitle = new LayoutParams(-1, -2); subtitle.topMargin = MirrorUi.dp(context, 3); labels.addView(pkg, subtitle);
            addView(labels, new LayoutParams(0, -2, 1));
            chevron = new ImageView(context); chevron.setImageDrawable(new MirrorUi.Glyph("chevron", theme.secondary));
            LayoutParams arrow = new LayoutParams(MirrorUi.dp(context, 18), MirrorUi.dp(context, 18)); arrow.setMarginStart(MirrorUi.dp(context, 8)); addView(chevron, arrow);
        }
    }
}
