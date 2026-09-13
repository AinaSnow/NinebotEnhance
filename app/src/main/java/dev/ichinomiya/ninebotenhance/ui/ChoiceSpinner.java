package dev.ichinomiya.ninebotenhance.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Keeps Spinner selection semantics while owning the complete picker theme. */
public final class ChoiceSpinner extends Spinner {
    private final Activity activity;
    private final MirrorUi theme;
    private AlertDialog popup;

    public ChoiceSpinner(Activity activity, MirrorUi theme, String title) {
        super(activity, Spinner.MODE_DIALOG);
        this.activity = activity; this.theme = theme; setPrompt(title);
    }
    @Override public boolean performClick() {
        if (!isEnabled() || getAdapter() == null) return false;
        if (popup != null && popup.isShowing()) return true;
        SpinnerAdapter source = getAdapter();
        int selected = getSelectedItemPosition(), pad = MirrorUi.dp(activity, 20), gap = MirrorUi.dp(activity, 8);
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);
        content.setBackground(theme.background(activity, theme.surface, 24, false)); content.setClipToOutline(true);
        TextView title = new TextView(activity); title.setText(getPrompt()); title.setTextSize(20); title.setTextColor(theme.text);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = MirrorUi.dp(activity, 16); content.addView(title, titleParams);

        ListView list = new ListView(activity) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int maximum = Math.round(activity.getWindowManager().getCurrentWindowMetrics().getBounds().height() * .6f);
                if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) maximum = Math.min(maximum, MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
            }
        };
        list.setBackgroundColor(theme.surface); list.setCacheColorHint(Color.TRANSPARENT);
        list.setDivider(new ColorDrawable(theme.surface)); list.setDividerHeight(gap);
        list.setSelector(theme.background(activity, theme.input, 14, false));
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return source.getCount(); }
            @Override public Object getItem(int position) { return source.getItem(position); }
            @Override public long getItemId(int position) { return source.getItemId(position); }
            @Override public View getView(int position, View convert, ViewGroup parent) {
                LinearLayout row = convert instanceof LinearLayout ? (LinearLayout)convert : new LinearLayout(activity);
                View previous = row.getChildCount() == 0 ? null : row.getChildAt(0);
                row.removeAllViews(); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(MirrorUi.dp(activity, 56));
                row.setBackground(theme.background(activity, position == selected ? theme.input : theme.surface, 14, false));
                row.setSelected(position == selected);
                View label = source.getDropDownView(position, previous, row);
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                ImageView mark = new ImageView(activity);
                mark.setImageDrawable(new MirrorUi.Glyph(position == selected ? "chosen" : "choice", position == selected ? theme.accent : theme.secondary));
                mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(MirrorUi.dp(activity, 22), MirrorUi.dp(activity, 22));
                markParams.setMarginStart(gap); markParams.setMarginEnd(MirrorUi.dp(activity, 12)); row.addView(mark, markParams);
                return row;
            }
        });
        content.addView(list, new LinearLayout.LayoutParams(-1, -2, 1));
        Button cancel = new Button(activity); cancel.setText("取消"); theme.button(cancel, null); cancel.setTextColor(theme.accent);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, -2);
        cancelParams.topMargin = MirrorUi.dp(activity, 16); content.addView(cancel, cancelParams);
        // No platform title/button panels: they inherit incompatible colours from the host Activity.
        AlertDialog dialog = new AlertDialog.Builder(activity).create();
        dialog.setView(content, 0, 0, 0, 0);
        popup = dialog;
        list.setOnItemClickListener((parent, view, position, id) -> { if (isEnabled()) setSelection(position); dialog.dismiss(); });
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(v -> { if (popup == dialog) popup = null; });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(theme.background(activity, theme.surface, 24, false));
        if (selected >= 0) list.setSelection(selected);
        return true;
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled && popup != null) popup.dismiss();
    }
    @Override protected void onDetachedFromWindow() {
        if (popup != null) popup.dismiss();
        super.onDetachedFromWindow();
    }
}
