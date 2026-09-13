package dev.ichinomiya.ninebotenhance.ui;

import android.app.Activity;
import android.view.View;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;
import dev.ichinomiya.ninebotenhance.platform.ModuleResources;
import java.io.*;

public final class AboutDialog {
    public static void show(Activity activity, View reference) {
        MirrorUi theme = new MirrorUi(activity, reference);
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        content.addView(DialogContent.text(activity, theme, "Ninebot Enhance", 24));
        TextView version = DialogContent.text(activity, theme, "版本 " + Protocol.VERSION + "\n" + Protocol.MODULE, 13);
        version.setTextColor(theme.secondary); content.addView(version);
        content.addView(DialogContent.text(activity, theme, "为九号出行添加应用投屏、系统录屏、虚拟屏预览与输入、会话统计。", 15));
        TextView copyright = DialogContent.text(activity, theme, "Copyright 2026 Ninebot Enhance contributors\nApache License 2.0\n本项目不是九号官方产品。", 13);
        copyright.setTextColor(theme.secondary); content.addView(copyright);
        resourceButton(activity, theme, content, "开源许可证", "META-INF/licenses/NinebotEnhance-Apache-2.0.txt");
        resourceButton(activity, theme, content, "第三方声明与致谢", "META-INF/NOTICE.txt");
        resourceButton(activity, theme, content, "Shizuku API 许可证", "META-INF/licenses/Shizuku-MIT.txt");
        resourceButton(activity, theme, content, "scrcpy 许可证", "META-INF/licenses/Apache-2.0.txt");
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        DialogContent.show(activity, theme, DialogContent.create(activity, theme, "关于", scroll));
    }
    private static void resourceButton(Activity activity, MirrorUi theme, LinearLayout content, String label, String resource) {
        Button button = new Button(activity); button.setText(label); theme.button(button, null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(MirrorUi.dp(activity, 20), MirrorUi.dp(activity, 8), MirrorUi.dp(activity, 20), MirrorUi.dp(activity, 8));
        content.addView(button, params);
        button.setOnClickListener(v -> {
            try { DialogContent.document(activity, theme, label, ModuleResources.text(resource)); }
            catch (IOException | RuntimeException e) { Toast.makeText(activity, "无法读取：" + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
    }
    private AboutDialog() {}
}
