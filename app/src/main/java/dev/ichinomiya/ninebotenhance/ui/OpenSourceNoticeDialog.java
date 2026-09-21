package dev.ichinomiya.ninebotenhance.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.OpenSourceNotice;

/** Shown once before the settings screen: the sentence has to be typed or filled with the button, then it is never asked again. */
final class OpenSourceNoticeDialog {
    static void show(Activity activity, View reference, FrameClient frames, Runnable accepted) {
        MirrorUi theme = new MirrorUi(activity, reference);
        int pad = MirrorUi.dp(activity, 20), gap = MirrorUi.dp(activity, 8), inner = MirrorUi.dp(activity, 12);
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(pad, gap, pad, gap);
        EditText input = new EditText(activity); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(OpenSourceNotice.REQUIRED); input.setHintTextColor(theme.secondary); input.setTextColor(theme.text); input.setTextSize(15);
        input.setBackgroundTintList(null); input.setBackground(theme.background(activity, theme.input, 12, false));
        input.setPadding(inner, inner, inner, inner); input.setMinimumHeight(MirrorUi.dp(activity, 48));
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        Button fill = new Button(activity); fill.setText("一键输入"); fill.setAllCaps(false); theme.button(fill, null); fill.setTextSize(13);
        fill.setMinHeight(0); fill.setMinimumHeight(0); fill.setMinWidth(0); fill.setMinimumWidth(0); fill.setPadding(inner, 0, inner, 0);
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(-2, MirrorUi.dp(activity, 48)); fillParams.setMarginStart(gap);
        row.addView(fill, fillParams);
        TextView title = DialogContent.text(activity, theme, "确认", 20);
        title.setPadding(title.getPaddingLeft(), pad, title.getPaddingRight(), inner);
        AlertDialog dialog = new AlertDialog.Builder(activity).setCustomTitle(title).setView(row)
                .setPositiveButton("确定", null).setNegativeButton("取消", null).create();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        DialogContent.show(activity, theme, dialog);
        Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Runnable refresh = () -> { if (confirm != null) confirm.setEnabled(OpenSourceNotice.matches(input.getText())); };
        refresh.run();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { refresh.run(); }
        });
        fill.setOnClickListener(v -> { input.setText(OpenSourceNotice.REQUIRED); input.setSelection(input.getText().length()); });
        if (confirm != null) confirm.setOnClickListener(v -> {
            if (!OpenSourceNotice.matches(input.getText())) return;
            frames.saveNoticeAccepted(); dialog.dismiss(); accepted.run();
        });
        input.requestFocus();
    }
    private OpenSourceNoticeDialog() {}
}
