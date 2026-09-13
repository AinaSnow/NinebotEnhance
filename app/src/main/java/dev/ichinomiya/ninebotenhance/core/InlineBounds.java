package dev.ichinomiya.ninebotenhance.core;

/** Clip the map's window-relative rectangle to the Activity content container. */
public final class InlineBounds {
    public static int[] clip(int x, int y, int width, int height, int hostWidth, int hostHeight) {
        if (width <= 0 || height <= 0 || hostWidth <= 0 || hostHeight <= 0) return null;
        long left = Math.max(0L, x), top = Math.max(0L, y);
        long right = Math.min((long)hostWidth, (long)x + width), bottom = Math.min((long)hostHeight, (long)y + height);
        if (right <= left || bottom <= top) return null;
        return new int[]{(int)left, (int)top, (int)(right - left), (int)(bottom - top)};
    }
    private InlineBounds() {}
}
