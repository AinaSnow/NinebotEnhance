import dev.ichinomiya.ninebotenhance.core.CalibrationPattern;
import dev.ichinomiya.ninebotenhance.core.DebugMode;
import dev.ichinomiya.ninebotenhance.diagnostics.EncodingDiagnostics;
import dev.ichinomiya.ninebotenhance.diagnostics.EncodingFormat;
import java.util.Arrays;
import java.util.Map;

final class CalibrationTests {
    static void run() {
        DebugMode mode = new DebugMode();
        CoreTests.check(!mode.unlocked() && !mode.enabled(), "debug is hidden and disabled on first install");
        CoreTests.check(!mode.setEnabled(true) && !mode.enabled(), "hidden debug cannot accidentally enable");
        for (int i = 0; i < 4; i++) CoreTests.check(!mode.tapVersion(), "fewer than five taps keep checkbox hidden");
        CoreTests.check(mode.tapVersion() && !mode.enabled(), "fifth tap reveals checkbox without changing the picture");
        CoreTests.check(mode.setEnabled(true) && mode.enabled(), "revealed checkbox selects calibration");
        CoreTests.check(!mode.setEnabled(true), "duplicate checkbox value does not change picture generation");
        CoreTests.check(mode.setEnabled(false) && !mode.enabled() && mode.unlocked(), "turning off restores normal output but keeps option visible");
        mode.restore(true, true); CoreTests.check(mode.unlocked() && mode.enabled(), "saved debug selection survives reload");
        mode.restore(false, true); CoreTests.check(!mode.enabled(), "invalid persisted hidden-enabled state normalizes to off");

        int width = 848, height = 480;
        int[] image = CalibrationPattern.render(width, height);
        CoreTests.check(image.length == width * height, "chart occupies the complete encoded frame, including top band");
        CoreTests.check(Arrays.equals(image, CalibrationPattern.render(width, height)), "chart is static across frames");
        CoreTests.check(Arrays.stream(image).allMatch(p -> (p >>> 24) == 255), "opaque chart cannot expose real app pixels");
        CoreTests.check(image[0] == 0xffff00ff && image[(height - 1) * width] == 0xff00ffff,
                "first and last output rows carry distinct cropping markers");
        CoreTests.check(image[5 * width] == 0xffffff00 && image[5 * width + width - 1] == 0xff00ff00,
                "left and right edge markers identify orientation");
        CoreTests.check(image[117 * width + 50] == 0xff355b7d && image[117 * width + 10] == 0xff8093a4,
                "50 px major and 10 px minor grid represent actual pixel coordinates");
        CoreTests.check(CalibrationPattern.render(720, 1280).length == 720 * 1280, "portrait encoder size is supported");
        CoreTests.check(CalibrationPattern.render(32, 32).length == 1024, "small valid canvas clips labels safely");
        CoreTests.rejects(() -> CalibrationPattern.render(0, 480), "empty chart rejected");
        CoreTests.rejects(() -> CalibrationPattern.render(Integer.MAX_VALUE, 480), "oversized chart rejected before allocation");

        EncodingDiagnostics encoding = new EncodingDiagnostics(line -> {});
        CoreTests.check(encoding.frameSize() == null, "no session does not invent codec dimensions");
        encoding.begin("test", true, 0); var session = encoding.active(); Object codec = new Object();
        encoding.associate(session, codec);
        encoding.configured(session, codec, EncodingFormat.read(Map.of("width", 848, "height", 480)));
        CoreTests.check(Arrays.equals(encoding.frameSize(), new int[]{848, 480}), "calibration follows accepted encoding dimensions");
        encoding.format(session, codec, EncodingFormat.read(Map.of("width", 800, "height", 448)), true, "output");
        CoreTests.check(Arrays.equals(encoding.frameSize(), new int[]{800, 448}), "actual output dimensions override configuration");
        int[] copy = encoding.frameSize(); copy[0] = 1;
        CoreTests.check(encoding.frameSize()[0] == 800, "caller cannot mutate diagnostic dimensions");
        encoding.format(session, codec, EncodingFormat.read(Map.of()), true, "incomplete output");
        CoreTests.check(Arrays.equals(encoding.frameSize(), new int[]{848, 480}), "partial output falls back to accepted configuration");
        encoding.retire(session, codec); CoreTests.check(encoding.frameSize() == null, "retired codec cannot determine calibration");
        encoding.configured(session, codec, EncodingFormat.read(Map.of("width", Integer.MAX_VALUE, "height", 480)));
        CoreTests.check(encoding.frameSize() == null, "unbounded codec size cannot allocate chart");
        encoding.stop("test", 1, null); CoreTests.check(encoding.frameSize() == null, "ended session cannot leak old calibration size");
    }
    /** Export the same pure pixel renderer used by Android for visual review. */
    public static void main(String[] args) throws Exception {
        var image = new java.awt.image.BufferedImage(848, 480, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 848, 480, CalibrationPattern.render(848, 480), 0, 848);
        javax.imageio.ImageIO.write(image, "png", new java.io.File(args[0]));
    }
}
