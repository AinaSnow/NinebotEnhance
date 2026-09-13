import dev.ichinomiya.ninebotenhance.core.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

public final class BandColorTests {
    static void run() {
        CoreTests.check(BandColor.parse("#242424") == DisplaySettings.DEFAULT_TOP_COLOR, "default band colour is opaque near-black grey");
        CoreTests.check(BandColor.parse(" 12a0E3 ") == 0xff12a0e3, "custom colour accepts RGB with optional hash and mixed case");
        CoreTests.check(BandColor.hex(0xff12a0e3).equals("#12A0E3"), "displayed colour uses six uppercase RGB digits");
        CoreTests.check(BandColor.parse("000000") == 0xff000000 && BandColor.parse("#FFFFFF") == 0xffffffff, "black and white remain opaque");
        CoreTests.rejects(() -> BandColor.parse(null), "empty colour is rejected");
        CoreTests.rejects(() -> BandColor.parse("#123"), "short colour cannot silently change meaning");
        CoreTests.rejects(() -> BandColor.parse("#ff242424"), "alpha input cannot silently truncate to another colour");
        CoreTests.rejects(() -> BandColor.parse("#GG2424"), "non-hex colour is rejected");
        CoreTests.rejects(() -> new DisplaySettings(860, 450, 160, 45, 0x80242424), "transparent colour rejected before frame allocation");
        DisplaySettings empty = DisplaySettings.read((key, fallback) -> fallback);
        CoreTests.check(empty.width == 848 && empty.height == 440 && empty.dpi == 160 && empty.topInset == 40 && empty.topColor == 0xff242424,
                "empty preferences, host cache and IPC share the new defaults");
        Map<String, Integer> legacy = Map.of("width", 860, "height", 480, "dpi", 200, "top_inset", 0);
        DisplaySettings kept = DisplaySettings.read(legacy::getOrDefault);
        CoreTests.check(kept.height == 480 && kept.dpi == 200 && kept.topInset == 0 && kept.topColor == 0xff242424,
                "saved sizes and explicitly disabled band survive upgrade; missing colour uses grey");
        Map<String, Integer> saved = Map.of("width", 900, "height", 500, "dpi", 160, "top_inset", 25, "top_color", 0xff12a0e3);
        DisplaySettings loaded = DisplaySettings.read(saved::getOrDefault);
        CoreTests.check(loaded.width == 900 && loaded.height == 500 && loaded.topInset == 25 && loaded.topColor == 0xff12a0e3 && loaded.frameHeight() == 525,
                "saved custom colour and band height restore with the same full frame extent");
        CoreTests.check(loaded.label().contains("#12A0E3"), "diagnostics identify the actual band colour");
        byte[] app = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
        byte[] expected = {0x12,(byte)0xa0,(byte)0xe3,(byte)255,0x12,(byte)0xa0,(byte)0xe3,(byte)255,
                1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
        for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
            ByteBuffer source = ByteBuffer.wrap(app).asReadOnlyBuffer(), output = ByteBuffer.allocateDirect(32).order(order);
            PixelPacking.rgba(source, 8, 4, 2, 2, output, 1, 0xff12a0e3);
            byte[] actual = new byte[output.remaining()]; output.duplicate().get(actual);
            CoreTests.check(Arrays.equals(expected, actual), "non-grey custom RGB has correct channels and opacity in both byte orders");
            CoreTests.check(output.limit() == 24 && output.position() == 0 && output.order() == order && source.position() == 0,
                    "colour fill preserves input position, output extent and byte order");
            PixelPacking.rgba(source, 8, 4, 2, 2, output, 0, 0xff12a0e3);
            actual = new byte[output.remaining()]; output.duplicate().get(actual);
            CoreTests.check(Arrays.equals(app, actual), "zero height disables the coloured band without stale rows");
            CoreTests.rejects(() -> PixelPacking.rgba(source, 8, 4, 2, 2, output, 1, 0x0012a0e3), "packing rejects transparent band colours");
            CoreTests.check(output.limit() == 16 && output.position() == 0, "invalid colour leaves the last valid output untouched");
        }
        ByteBuffer fullApp = ByteBuffer.allocate(empty.width * empty.height * 4);
        fullApp.putInt(fullApp.capacity() - 4, 0x102030ff);
        ByteBuffer fullOutput = ByteBuffer.allocate(empty.width * empty.frameHeight() * 4);
        PixelPacking.rgba(fullApp, empty.width * 4, 4, empty.width, empty.height, fullOutput, empty.topInset, empty.topColor);
        CoreTests.check(fullOutput.getInt(0) == 0x242424ff && fullOutput.getInt(empty.width * empty.topInset * 4 - 4) == 0x242424ff,
                "all 40 default top rows span the full 848-pixel width in opaque grey");
        CoreTests.check(fullOutput.limit() == 848 * 480 * 4 && fullOutput.getInt(fullOutput.limit() - 4) == 0x102030ff,
                "default coloured frame retains the final app pixel at the bottom of the 480-row output");
    }
}
