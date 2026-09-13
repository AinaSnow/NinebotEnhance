package dev.ichinomiya.ninebotenhance.diagnostics;

import dev.ichinomiya.ninebotenhance.ipc.Protocol;

import android.os.Build;
import android.util.Log;
import java.util.ArrayDeque;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded metadata log. Never writes captured images, payload bytes, or account data. */
public final class Diagnostics {
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    public static synchronized void add(String message) {
        String clean = message.replace('\r', ' ').replace('\n', ' ');
        if (clean.length() > 1600) clean = clean.substring(0, 1600);
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + " " + clean;
        LINES.addLast(line);
        while (LINES.size() > 240) LINES.removeFirst();
        Log.i(Protocol.TAG, clean);
    }
    public static synchronized String text() {
        return "Ninebot Enhance " + Protocol.VERSION + "\nAndroid " + Build.VERSION.RELEASE
                + " / SDK " + Build.VERSION.SDK_INT + " / " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nVirtualDisplay / MediaProjection\nraw RGBA Surface\nno JPEG IPC\n\n" + String.join("\n", LINES);
    }
    public static synchronized String recent(int limit) { return LogDigest.recent(String.join("\n", LINES), limit); }
    private Diagnostics() {}
}
