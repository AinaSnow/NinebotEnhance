package dev.ichinomiya.ninebotenhance.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PixelPacking {
    /** Reads no last-row padding; some ImageReader buffers do not expose that padding. */
    public static void rgba(ByteBuffer input, int rowStride, int pixelStride, int width, int height, ByteBuffer output) {
        rgba(input, rowStride, pixelStride, width, height, output, 0);
    }
    /** Prepend an opaque full-width black band and copy every source row, including the bottom edge. */
    public static void rgba(ByteBuffer input, int rowStride, int pixelStride, int width, int height, ByteBuffer output, int topInset) {
        rgba(input, rowStride, pixelStride, width, height, output, topInset, 0xff000000);
    }
    /** ARGB setting to opaque RGBA bytes, independent of the output ByteBuffer's byte order. */
    public static void rgba(ByteBuffer input, int rowStride, int pixelStride, int width, int height, ByteBuffer output, int topInset, int topColor) {
        if (width < 1 || height < 1 || pixelStride < 4 || rowStride < (long) width * pixelStride
                || (long) width * (height + (long) topInset) * 4 > output.capacity()) throw new IllegalArgumentException("Invalid RGBA layout");
        if (topInset < 0 || topInset >= height) throw new IllegalArgumentException("Invalid top inset");
        BandColor.requireOpaque(topColor);
        int base = input.position();
        long lastByte = (long) base + (long) (height - 1) * rowStride + (long) (width - 1) * pixelStride + 4;
        if (lastByte > input.limit()) throw new IllegalArgumentException("Incomplete RGBA buffer");
        output.clear();
        int rgba = ((topColor & 0x00ffffff) << 8) | 255;
        int packedColor = output.order() == ByteOrder.BIG_ENDIAN ? rgba : Integer.reverseBytes(rgba);
        for (int i = 0; i < width * topInset; i++) output.putInt(packedColor);
        for (int y = 0; y < height; y++) {
            int start = base + y * rowStride;
            if (pixelStride == 4) {
                ByteBuffer row = input.duplicate(); row.position(start); row.limit(start + width * 4); output.put(row);
            } else {
                for (int x = 0; x < width; x++) for (int c = 0; c < 4; c++) output.put(input.get(start + x * pixelStride + c));
            }
        }
        output.flip();
    }
    private PixelPacking() {}
}
