package dev.ichinomiya.ninebotenhance.core;

/** Resolve host theme without mistaking secondary grey text or a coloured button for light mode. */
public final class ThemeMode {
    public static boolean dark(boolean night, Integer surface, Integer text) {
        if (night) return true;
        Boolean background = surfaceDark(surface);
        if (background != null) return background;
        if (neutralOpaque(text)) {
            double light = luminance(text);
            if (light >= .65) return true;
            if (light <= .12) return false;
        }
        return false;
    }
    public static Boolean surfaceDark(Integer color) {
        if (!neutralOpaque(color)) return null;
        double light = luminance(color);
        return light <= .25 ? Boolean.TRUE : light >= .65 ? Boolean.FALSE : null;
    }
    private static boolean neutralOpaque(Integer color) {
        if (color == null || (color >>> 24) < 240) return false;
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 48;
    }
    private static double luminance(int color) {
        return .2126 * linear((color >>> 16) & 255) + .7152 * linear((color >>> 8) & 255) + .0722 * linear(color & 255);
    }
    private static double linear(int value) {
        double channel = value / 255.;
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }
    private ThemeMode() {}
}
