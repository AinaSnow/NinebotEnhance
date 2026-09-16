package dev.ichinomiya.ninebotenhance.notification;

import android.app.NotificationManager;
import android.content.*;
import dev.ichinomiya.ninebotenhance.core.NotificationTimeline;
import java.util.*;

public final class NotificationPreferences {
    private final Context context;
    private final SharedPreferences prefs;
    public NotificationPreferences(Context context) { this.context=context; prefs=context.getSharedPreferences("notifications",Context.MODE_PRIVATE); }
    public static ComponentName listener(Context c) { return new ComponentName(c,MirrorNotificationListener.class); }
    public boolean granted() { try { NotificationManager manager=context.getSystemService(NotificationManager.class);return manager!=null&&manager.isNotificationListenerAccessGranted(listener(context)); }catch(RuntimeException e){return false;} }
    public boolean enabled() { return prefs.getBoolean("enabled",false); }
    public int seconds() { return NotificationTimeline.clampSeconds(prefs.getInt("seconds",NotificationTimeline.DEFAULT_SECONDS)); }
    public int width(){return Math.max(NotificationTimeline.MIN_WIDTH,Math.min(NotificationTimeline.MAX_WIDTH,prefs.getInt("width",NotificationTimeline.DEFAULT_WIDTH)));}
    public int limit(){return NotificationTimeline.clampLimit(prefs.getInt("limit",NotificationTimeline.DEFAULT_LIMIT));}
    public Set<String> packages() { return new HashSet<>(prefs.getStringSet("packages",Collections.emptySet())); }
    public void save(boolean enabled,int seconds,Set<String> packages) {
        save(enabled,seconds,width(),packages);
    }
    public void save(boolean enabled,int seconds,int width,Set<String> packages){save(enabled,seconds,width,limit(),packages);}
    public void save(boolean enabled,int seconds,int width,int limit,Set<String> packages){
        NotificationTimeline.duration(seconds);
        NotificationTimeline.requireWidth(width);
        if(limit<NotificationTimeline.MIN_LIMIT||limit>NotificationTimeline.MAX_LIMIT)throw new IllegalArgumentException("同时显示条数须为 1–5");
        if(enabled&&!granted())throw new IllegalStateException("请先授予通知使用权");
        prefs.edit().putBoolean("enabled",enabled).putInt("seconds",seconds).putInt("width",width).putInt("limit",limit).putStringSet("packages",new HashSet<>(packages)).apply();
        NotificationHub.get(context).clear();
    }
}
