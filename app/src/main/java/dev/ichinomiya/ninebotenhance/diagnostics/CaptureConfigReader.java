package dev.ichinomiya.ninebotenhance.diagnostics;

import java.lang.reflect.*;
import java.util.*;

/** Bounded field reads only: no toString/getter calls on target objects, no arbitrary strings. */
public final class CaptureConfigReader {
    public static boolean videoConfig(Class<?> type) { return type.getName().startsWith("cn.ninebot.capture.") && type.getSimpleName().equals("VideoConfig"); }
    public static String fields(Object object, boolean allNumericFields) {
        if (object == null) return "未读取";
        List<String> values = new ArrayList<>();
        int depth = 0, inspected = 0;
        for (Class<?> type = object.getClass(); type != null && type != Object.class && depth++ < 5; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (++inspected > 160 || values.size() >= 24) break;
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                String name = field.getName();
                boolean relevant = name.toLowerCase(Locale.ROOT).matches(".*(width|height|fps|framerate|frame_rate|bitrate|bit_rate|mime|codec|quality|interval).*");
                if (!allNumericFields && !relevant && !videoConfig(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    String scalar = scalar(value, relevant);
                    if (scalar != null) values.add(name + "=" + scalar);
                    else if (!allNumericFields && value != null && videoConfig(field.getType())) values.add(name + "={" + fields(value, true) + "}");
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        return values.isEmpty() ? "未读取" : String.join(",", values);
    }
    public static String scalar(Object value, boolean allowCodecString) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Boolean) return value.toString();
        if (value instanceof Float || value instanceof Double) return Double.isFinite(((Number)value).doubleValue()) ? value.toString() : null;
        if (allowCodecString && value instanceof String && ((String)value).matches("(?:video/[A-Za-z0-9.+_-]{1,60}|[A-Za-z0-9_.-]{1,80})")) return (String)value;
        return null;
    }
    private CaptureConfigReader() {}
}
