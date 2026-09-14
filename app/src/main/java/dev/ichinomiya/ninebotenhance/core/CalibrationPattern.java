package dev.ichinomiya.ninebotenhance.core;

import java.util.Arrays;

/** Opaque pixel-coordinate chart. Shared verbatim by Android output and host PNG verification. */
public final class CalibrationPattern {
    public static final int STEP = 10, MAJOR = 50, CELL = 100;
    private static final String[] FONT = {
        "01110/10001/10011/10101/11001/10001/01110", // 0
        "00100/01100/00100/00100/00100/00100/01110",
        "01110/10001/00001/00010/00100/01000/11111",
        "11110/00001/00001/01110/00001/00001/11110",
        "00010/00110/01010/10010/11111/00010/00010",
        "11111/10000/10000/11110/00001/00001/11110",
        "01110/10000/10000/11110/10001/10001/01110",
        "11111/00001/00010/00100/01000/01000/01000",
        "01110/10001/10001/01110/10001/10001/01110",
        "01110/10001/10001/01111/00001/00001/01110",
        "10001/10001/01010/00100/01010/10001/10001", // X
        "10001/10001/01010/00100/00100/00100/00100", // Y
        "10001/10001/10001/10101/10101/10101/01010", // W
        "10001/10001/10001/11111/10001/10001/10001", // H
    };
    public static int[] render(int width, int height) {
        if (width < 32 || height < 32 || width > 4096 || height > 2160)
            throw new IllegalArgumentException("Calibration size out of bounds");
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean light = ((x / CELL + y / CELL) & 1) == 0;
            int color = light ? 0xffd2dbe3 : 0xff9aa8b6;
            if (x % STEP == 0 || y % STEP == 0) color = light ? 0xffb2c0cc : 0xff8093a4;
            if (x % MAJOR == 0 || y % MAJOR == 0) color = 0xff355b7d;
            if (x % CELL == 0 || y % CELL == 0) color = 0xff123452;
            pixels[y * width + x] = color;
        }
        int scale = width >= 640 && height >= 360 ? 2 : 1;
        for (int y = 0; y < height - 36 * scale; y += CELL) for (int x = 0; x < width - 35 * scale; x += CELL) {
            int labelX = x == 0 ? 30 : x + 8;
            text(pixels, width, height, "X" + x, labelX, y + 22, scale);
            text(pixels, width, height, "Y" + y, labelX, y + 22 + 9 * scale, scale);
        }
        for (int x = MAJOR; x < width - 24; x += MAJOR) {
            String value = String.valueOf(x);
            text(pixels, width, height, value, x + 3, 5, 1);
            text(pixels, width, height, value, x + 3, height - 13, 1);
        }
        for (int y = MAJOR; y < height - 24; y += MAJOR) {
            String value = String.valueOf(y);
            text(pixels, width, height, value, 5, y + 3, 1);
            text(pixels, width, height, value, width - value.length() * 6 - 5, y + 3, 1);
        }
        String dimensions = "W" + width + " H" + height;
        text(pixels, width, height, dimensions, Math.max(4, (width - dimensions.length() * 6 * scale) / 2),
                Math.min(height - 7 * scale - 4, height / 2 + 14 * scale), scale);
        // Distinct edge phases expose cropping, mirroring and rotation without guessing where (0,0) is.
        for (int x = 0; x < width; x++) {
            rect(pixels, width, height, x, 0, 1, 3, (x / STEP & 1) == 0 ? 0xffff00ff : 0xff000000);
            rect(pixels, width, height, x, height - 3, 1, 3, (x / STEP & 1) == 0 ? 0xff00ffff : 0xff000000);
        }
        for (int y = 3; y < height - 3; y++) {
            rect(pixels, width, height, 0, y, 3, 1, (y / STEP & 1) == 0 ? 0xffffff00 : 0xff000000);
            rect(pixels, width, height, width - 3, y, 3, 1, (y / STEP & 1) == 0 ? 0xff00ff00 : 0xff000000);
        }
        return pixels;
    }
    private static void text(int[] pixels, int width, int height, String value, int x, int y, int scale) {
        rect(pixels, width, height, x - 2, y - 2, value.length() * 6 * scale + 3, 7 * scale + 4, 0xff17212b);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int glyph = c >= '0' && c <= '9' ? c - '0' : c == 'X' ? 10 : c == 'Y' ? 11 : c == 'W' ? 12 : c == 'H' ? 13 : -1;
            if (glyph < 0) continue;
            for (int row = 0; row < 7; row++) for (int col = 0; col < 5; col++)
                if (FONT[glyph].charAt(row * 6 + col) == '1')
                    rect(pixels, width, height, x + (i * 6 + col) * scale, y + row * scale, scale, scale, 0xffffffff);
        }
    }
    private static void rect(int[] pixels, int width, int height, int x, int y, int w, int h, int color) {
        int left = Math.max(0, x), right = Math.min(width, x + w);
        if (left >= right) return;
        for (int row = Math.max(0, y); row < Math.min(height, y + h); row++)
            Arrays.fill(pixels, row * width + left, row * width + right, color);
    }
    private CalibrationPattern() {}
}
