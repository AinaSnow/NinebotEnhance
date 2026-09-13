package dev.ichinomiya.ninebotenhance.core;

/** Phone-only clockwise quarter-turn followed by FIT; independent of the virtual display rotation. */
public final class PreviewTransform {
    public final TouchMapping fit;
    public final float[] input;
    private final int topInset, contentHeight;
    public PreviewTransform(int width, int height, int viewWidth, int viewHeight, boolean rotated) {
        this(width, height, viewWidth, viewHeight, rotated, 0);
    }
    public PreviewTransform(int width, int height, int viewWidth, int viewHeight, boolean rotated, int topInset) {
        if (topInset < 0 || topInset >= height || (long) height + topInset > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid top inset");
        this.topInset = topInset; contentHeight = height;
        int frameHeight = height + topInset;
        fit = new TouchMapping(rotated ? frameHeight : width, rotated ? width : frameHeight, viewWidth, viewHeight);
        float s = fit.scale;
        input = rotated ? new float[]{0, s, -fit.top * s, -s, 0, frameHeight + fit.left * s, 0, 0, 1}
                : new float[]{s, 0, -fit.left * s, 0, s, -fit.top * s, 0, 0, 1};
        input[5] -= topInset;
    }
    /** Reject phone FIT margins and the composited top band before creating an app gesture. */
    public boolean contains(float x, float y) {
        if (!fit.contains(x, y)) return false;
        if (topInset == 0) return true;
        float sourceY = input[3] * x + input[4] * y + input[5];
        return sourceY >= 0 && sourceY < contentHeight;
    }
}
