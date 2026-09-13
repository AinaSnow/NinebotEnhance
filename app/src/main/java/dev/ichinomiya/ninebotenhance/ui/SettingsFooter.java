package dev.ichinomiya.ninebotenhance.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.*;

/** Four persistent actions fit without depending on the host's three-button AlertDialog layout. */
final class SettingsFooter extends LinearLayout {
    final Button about, logs, close, save;
    SettingsFooter(Context context, MirrorUi theme) {
        super(context); setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        int pad = MirrorUi.dp(context, 8); setPadding(pad, pad / 2, pad, pad / 2);
        about = action("关于", theme); logs = action("日志", theme); close = action("关闭", theme); save = action("保存", theme);
    }
    private Button action(String label, MirrorUi theme) {
        Button button = new Button(getContext()); button.setText(label); theme.button(button, null);
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{theme.secondary, theme.accent}));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22888899), theme.background(getContext(), theme.surface, 12, false), null));
        button.setPadding(MirrorUi.dp(getContext(), 4), MirrorUi.dp(getContext(), 12), MirrorUi.dp(getContext(), 4), MirrorUi.dp(getContext(), 12));
        button.setMinimumHeight(MirrorUi.dp(getContext(), 48));
        addView(button, new LayoutParams(0, -2, 1)); return button;
    }
}
