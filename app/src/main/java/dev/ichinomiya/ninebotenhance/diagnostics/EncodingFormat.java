package dev.ichinomiya.ninebotenhance.diagnostics;

import java.util.Map;

/** A small immutable copy of codec metadata, never a reference to a codec's mutable MediaFormat. */
public record EncodingFormat(String mime, Long width, Long height, Double fps, Long bitrate,
                             Long cropLeft, Long cropTop, Long cropRight, Long cropBottom) {
    public static final String UNKNOWN = "未读取";
    public static EncodingFormat read(Map<String, ?> values) {
        Object mime = values.get("mime");
        return new EncodingFormat(mime instanceof String && ((String)mime).matches("video/[A-Za-z0-9.+_-]{1,60}") ? (String)mime : UNKNOWN,
                integer(values.get("width"), true), integer(values.get("height"), true), positive(values.get("frame-rate")),
                integer(values.get("bitrate"), true), integer(values.get("crop-left"), false), integer(values.get("crop-top"), false),
                integer(values.get("crop-right"), false), integer(values.get("crop-bottom"), false));
    }
    private static Double positive(Object value) {
        if (!(value instanceof Number)) return null;
        double number = ((Number)value).doubleValue();
        return Double.isFinite(number) && number > 0 ? (value instanceof Float ? Double.valueOf(value.toString()) : number) : null;
    }
    private static Long integer(Object value, boolean positive) {
        if (!(value instanceof Number)) return null;
        double number = ((Number)value).doubleValue();
        return Double.isFinite(number) && number >= (positive ? 1 : 0) && number <= Integer.MAX_VALUE && number == Math.rint(number) ? (long)number : null;
    }
    public static String value(Object value) { return value == null ? UNKNOWN : value.toString(); }
    public String dimensions() { return value(width) + "x" + value(height); }
    public String describe() {
        String crop = cropLeft != null && cropTop != null && cropRight != null && cropBottom != null
                && cropRight >= cropLeft && cropBottom >= cropTop && width != null && height != null && cropRight < width && cropBottom < height
                ? (cropRight - cropLeft + 1) + "x" + (cropBottom - cropTop + 1) + "@" + cropLeft + "," + cropTop : UNKNOWN;
        return "mime=" + mime + " size=" + dimensions() + " formatFps=" + value(fps) + " bitrateBps=" + value(bitrate) + " visibleCrop=" + crop;
    }
}
