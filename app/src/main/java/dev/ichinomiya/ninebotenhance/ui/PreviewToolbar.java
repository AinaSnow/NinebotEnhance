package dev.ichinomiya.ninebotenhance.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

/** Shared phone preview controls, with a second row when four labelled actions cannot fit. */
public final class PreviewToolbar extends LinearLayout {
    private final TextView title;
    private final LinearLayout actions;
    private MirrorUi theme;
    private final PreviewPicture picture;
    private final Button back, rotate, keyboard, simulate, stop;
    private Button dashboard;
    private boolean configured, wasCompact;
    public PreviewToolbar(Context context, MirrorUi theme, String label, PreviewPicture picture, Runnable end, Runnable diagnostics, Runnable simulateNotification,
                          java.util.function.BooleanSupplier dashboardDark, Runnable toggleDashboardTheme) {
        super(context); this.theme = theme; this.picture = picture;
        setGravity(Gravity.CENTER_VERTICAL); setBackgroundColor(theme.surface);
        setPadding(dp(10), dp(4), dp(10), dp(4));
        title = new TextView(context); title.setText(label); title.setTextSize(14); title.setTextColor(theme.text);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL); title.setOnLongClickListener(v -> { diagnostics.run(); return true; });
        addView(title);
        actions = new LinearLayout(context); actions.setGravity(Gravity.CENTER_VERTICAL); addView(actions);
        back = action("返回", "back", picture::back);
        rotate = action("横屏", "rotate", picture::toggleRotation);
        keyboard = action("输入法", "keyboard", picture::toggleKeyboard);
        dashboard = action(dashboardDark.getAsBoolean() ? "深色" : "浅色", null, () -> {
            toggleDashboardTheme.run(); dashboard.setText(dashboardDark.getAsBoolean() ? "深色" : "浅色"); dashboard.setContentDescription(dashboard.getText());
        });
        // Only the local simulation offers the synthetic notification card; a null action leaves the button out.
        simulate = simulateNotification == null ? null : action("通知", null, simulateNotification);
        stop = action("结束", "stop", end);
        picture.onControlsChanged = () -> {
            rotate.setText(picture.rotated() ? "还原" : "横屏");
            keyboard.setText(picture.keyboardActive() ? "收起" : "输入法");
            style(rotate, "rotate", picture.rotated()); style(keyboard, "keyboard", picture.keyboardActive());
        };
        picture.onControlsChanged.run();
    }
    public void applyTheme(MirrorUi next) {
        if (theme.dark == next.dark) return;
        theme = next; setBackgroundColor(theme.surface); title.setTextColor(theme.text);
        style(back, "back", false); style(rotate, "rotate", picture.rotated());
        style(keyboard, "keyboard", picture.keyboardActive()); style(dashboard, null, false); if (simulate != null) style(simulate, null, false); style(stop, "stop", false);
    }
    private Button action(String label, String icon, Runnable action) {
        Button button = new Button(getContext()); button.setText(label); button.setContentDescription(label);
        style(button, icon, false); button.setOnClickListener(v -> action.run());
        LayoutParams params = new LayoutParams(0, dp(44), 1); if (actions.getChildCount() > 0) params.leftMargin = dp(6);
        actions.addView(button, params); return button;
    }
    private void style(Button button, String icon, boolean active) {
        theme.button(button, null); button.setTextSize(12); button.setPadding(dp(6), 0, dp(6), 0);
        button.setTextColor(active ? theme.accent : theme.text);
        // Text only: with five labelled actions there is no room for glyphs.
        button.setCompoundDrawablesRelative(null, null, null, null); button.setSelected(active);
        button.setContentDescription(button.getText());
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean compact = MeasureSpec.getSize(widthSpec) < dp(640);
        if (!configured || wasCompact != compact) {
            configured = true; wasCompact = compact; setOrientation(compact ? VERTICAL : HORIZONTAL);
            title.setLayoutParams(compact ? new LayoutParams(-1, dp(28)) : new LayoutParams(0, dp(48), 1));
            actions.setLayoutParams(new LayoutParams(compact ? -1 : dp(simulate == null ? 440 : 500), dp(48)));
        }
        super.onMeasure(widthSpec, heightSpec);
    }
    private int dp(int value) { return MirrorUi.dp(getContext(), value); }
}
