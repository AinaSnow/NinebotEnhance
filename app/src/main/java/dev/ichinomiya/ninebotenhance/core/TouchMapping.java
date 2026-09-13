package dev.ichinomiya.ninebotenhance.core;

/** Same FIT geometry for preview pixels and input. Black bars must never generate a down event. */
public final class TouchMapping {
    public final float left, top, right, bottom, scale;
    public TouchMapping(int width, int height, int viewWidth, int viewHeight) {
        float[] fit = Geometry.fit(width, height, viewWidth, viewHeight);
        left = fit[0]; top = fit[1]; right = fit[2]; bottom = fit[3]; scale = width / (right - left);
    }
    public boolean contains(float x, float y) { return x >= left && x < right && y >= top && y < bottom; }
    public float x(float value) { return (value - left) * scale; }
    public float y(float value) { return (value - top) * scale; }
}
