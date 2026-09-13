package dev.ichinomiya.ninebotenhance.core;

/** Pure geometry, shared by capture sizing and the receiving encoder path. */
public final class Geometry {
    public static int[] captureSize(int w, int h, int longest) {
        if (w <= 0 || h <= 0 || longest < 2) throw new IllegalArgumentException("Invalid dimensions");
        double scale = Math.min(1.0, (double) longest / Math.max(w, h));
        return new int[] {Math.max(2, (int) (w * scale) & ~1), Math.max(2, (int) (h * scale) & ~1)};
    }
    public static float[] fit(int sw, int sh, int dw, int dh) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) throw new IllegalArgumentException("Invalid dimensions");
        float scale = Math.min((float) dw / sw, (float) dh / sh);
        float w = sw * scale, h = sh * scale;
        return new float[] {(dw - w) / 2f, (dh - h) / 2f, (dw + w) / 2f, (dh + h) / 2f};
    }
    private Geometry() {}
}
