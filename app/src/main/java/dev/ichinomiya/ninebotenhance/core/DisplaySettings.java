package dev.ichinomiya.ninebotenhance.core;

/** Bounds apply before allocating any graphics buffers, in both processes. */
public final class DisplaySettings {
    public final int width, height, dpi, topInset, topColor;
    public DisplaySettings(int width, int height, int dpi) {
        this(width, height, dpi, 0);
    }
    public DisplaySettings(int width, int height, int dpi, int topInset) {
        this(width, height, dpi, topInset, DEFAULT_TOP_COLOR);
    }
    public DisplaySettings(int width, int height, int dpi, int topInset, int topColor) {
        if (width < 320 || height < 320 || width > 1920 || height > 1920 || (width & 1) != 0 || (height & 1) != 0
                || (long)width * height > 2073600 || dpi < 100 || dpi > 480
                || Math.min(width, height) * 160L / dpi < 160)
            throw new IllegalArgumentException("宽高需为 320–1920 的偶数，总像素不超过 1920×1080；DPI 为 100–480，最短边至少 160 dp。");
        if (topInset < 0 || topInset >= height)
            throw new IllegalArgumentException("顶部黑边高度需为 0–" + (height - 1) + " 像素，0 表示关闭。");
        BandColor.requireOpaque(topColor);
        this.width = width; this.height = height; this.dpi = dpi; this.topInset = topInset; this.topColor = topColor;
    }
    public static final int DEFAULT_WIDTH = 848, DEFAULT_HEIGHT = 440, DEFAULT_DPI = 160, DEFAULT_TOP_INSET = 40;
    public static final int DEFAULT_TOP_COLOR = 0xff242424;
    public static DisplaySettings defaults() { return new DisplaySettings(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_DPI, DEFAULT_TOP_INSET); }
    @FunctionalInterface public interface IntSetting { int get(String key, int fallback); }
    /** Shared defaults for the service preferences, host cache and IPC; explicit saved zeros remain zeros. */
    public static DisplaySettings read(IntSetting values) {
        return new DisplaySettings(values.get("width", DEFAULT_WIDTH), values.get("height", DEFAULT_HEIGHT), values.get("dpi", DEFAULT_DPI),
                values.get("top_inset", DEFAULT_TOP_INSET), values.get("top_color", DEFAULT_TOP_COLOR));
    }
    /** The app keeps width x height; the coloured band adds rows to the composed output only. */
    public int frameHeight() { return height + topInset; }
    public String label() { return width + " × " + height + "，" + dpi + " DPI"
            + (topInset == 0 ? "" : "，顶部黑边 " + topInset + " px，颜色 " + BandColor.hex(topColor)
                    + "（整帧 " + width + " × " + frameHeight() + "）"); }
    public static String shellQuote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
