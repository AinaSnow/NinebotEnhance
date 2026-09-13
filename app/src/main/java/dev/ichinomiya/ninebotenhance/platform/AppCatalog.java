package dev.ichinomiya.ninebotenhance.platform;

import dev.ichinomiya.ninebotenhance.diagnostics.LogDigest;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;

import android.content.*;
import android.content.pm.*;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.*;

/** Queries launcher entries for settings only. No activity or UI is placed on the virtual display. */
public final class AppCatalog {
    public static final String SELECTED = "launch_app", APPS = "launch_apps";
    private static boolean eligible(ActivityInfo info) {
        return info != null && info.exported && info.enabled && info.applicationInfo != null && info.applicationInfo.enabled
                && !Protocol.MODULE.equals(info.packageName) && !Protocol.TARGET.equals(info.packageName);
    }
    public static ArrayList<Bundle> choices(PackageManager pm) {
        Map<String, Bundle> packages = new TreeMap<>();
        for (ResolveInfo entry : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            ActivityInfo info = entry.activityInfo;
            if (!eligible(info) || packages.containsKey(info.packageName)) continue;
            Bundle app = new Bundle();
            app.putString("component", new ComponentName(info.packageName, info.name).flattenToString());
            app.putString("package", info.packageName);
            try { app.putString("label", LogDigest.head(info.applicationInfo.loadLabel(pm).toString(), 120)); }
            catch (RuntimeException e) { app.putString("label", info.packageName); }
            packages.put(info.packageName, app);
        }
        ArrayList<Bundle> apps = new ArrayList<>(packages.values()); Collator collator = Collator.getInstance();
        apps.sort((a, b) -> {
            int order = collator.compare(a.getString("label"), b.getString("label"));
            return order != 0 ? order : a.getString("package").compareTo(b.getString("package"));
        });
        return apps;
    }
    public static ComponentName requireLauncher(PackageManager pm, String flattened) {
        ComponentName component = flattened == null ? null : ComponentName.unflattenFromString(flattened);
        if (component == null) throw new IllegalArgumentException("请先在设置中选择启动应用并保存");
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(component.getPackageName());
        for (ResolveInfo entry : pm.queryIntentActivities(query, 0)) {
            ActivityInfo info = entry.activityInfo;
            if (eligible(info) && component.getPackageName().equals(info.packageName) && component.getClassName().equals(info.name))
                return component;
        }
        throw new IllegalArgumentException("所选应用已卸载、停用或入口不可用，请回设置重新选择");
    }
    public static Intent launchIntent(PackageManager pm, ComponentName component) throws PackageManager.NameNotFoundException {
        requireLauncher(pm, component == null ? null : component.flattenToString());
        Intent intent = pm.getLaunchIntentForPackage(component.getPackageName());
        if (intent == null) intent = pm.getLeanbackLaunchIntentForPackage(component.getPackageName());
        if (intent == null || intent.getComponent() == null) throw new IllegalArgumentException("所选应用没有标准启动入口");
        ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
        if (!eligible(info) || !component.getPackageName().equals(info.packageName)) throw new SecurityException("所选应用的标准入口不可用");
        return intent;
    }
    public static Bitmap icon(PackageManager pm, String selected) throws PackageManager.NameNotFoundException {
        ComponentName component = requireLauncher(pm, selected);
        Drawable drawable = pm.getApplicationIcon(component.getPackageName());
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 96, 96); drawable.draw(new Canvas(bitmap)); return bitmap;
    }
    private AppCatalog() {}
}
