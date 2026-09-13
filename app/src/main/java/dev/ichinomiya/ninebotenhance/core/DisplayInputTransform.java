package dev.ichinomiya.ninebotenhance.core;

/** Map fixed RGBA Surface coordinates into a ROTATES_WITH_CONTENT display's logical space. */
public final class DisplayInputTransform {
    public static float[] matrix(int rotation, int width, int height, int logicalWidth, int logicalHeight) {
        if (rotation < 0 || rotation > 3 || width <= 0 || height <= 0 || logicalWidth <= 0 || logicalHeight <= 0)
            throw new IllegalArgumentException("Invalid display transform");
        float sx = (float)logicalWidth / ((rotation & 1) == 0 ? width : height);
        float sy = (float)logicalHeight / ((rotation & 1) == 0 ? height : width);
        // Same physical-to-logical rotation convention as Android RotationUtils.
        switch (rotation) {
            case 0: return new float[]{sx, 0, 0, 0, sy, 0, 0, 0, 1};
            case 1: return new float[]{0, sx, 0, -sy, 0, width * sy, 0, 0, 1};
            case 2: return new float[]{-sx, 0, width * sx, 0, -sy, height * sy, 0, 0, 1};
            default: return new float[]{0, -sx, height * sx, sy, 0, 0, 0, 0, 1};
        }
    }
    private DisplayInputTransform() {}
}
