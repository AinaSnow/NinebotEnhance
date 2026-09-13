import dev.ichinomiya.ninebotenhance.core.*;
import dev.ichinomiya.ninebotenhance.ipc.*;
import dev.ichinomiya.ninebotenhance.hook.*;
import dev.ichinomiya.ninebotenhance.diagnostics.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class CoreTests {
    static int assertions;
    static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    static void close(float a, float b, String message) { check(Math.abs(a - b) < .02, message); }
    static void rejects(Runnable action, String message) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, message);
    }
    static void mapped(float[] m, float x, float y, float expectedX, float expectedY) {
        close(m[0] * x + m[1] * y + m[2], expectedX, "rotated touch x");
        close(m[3] * x + m[4] * y + m[5], expectedY, "rotated touch y");
    }
    static void componentContractTests() throws Exception {
        // Class literals are also used by the client binding: moving the service must update the manifest.
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new java.io.File("app/src/main/AndroidManifest.xml"));
        String[] components = {dev.ichinomiya.ninebotenhance.client.ServiceBridge.SERVICE_CLASS,
                dev.ichinomiya.ninebotenhance.service.RootBridgeProvider.class.getName()};
        for (int i = 0; i < components.length; i++) {
            var nodes = document.getElementsByTagName(i == 0 ? "service" : "provider");
            org.w3c.dom.Element found = null;
            for (int j = 0; j < nodes.getLength(); j++) {
                var element = (org.w3c.dom.Element) nodes.item(j);
                String name = element.getAttribute("android:name");
                if (name.startsWith(".")) name = Protocol.MODULE + name;
                else if (!name.contains(".")) name = Protocol.MODULE + "." + name;
                if (components[i].equals(name)) found = element;
            }
            check(found != null && "true".equals(found.getAttribute("android:exported")),
                    "compiled broker component is declared and exported for authenticated cross-process IPC");
            check(found != null && found.getAttribute("android:process").isEmpty(),
                    "broker components share the module process with ShizukuProvider initialization");
        }
        var providers = document.getElementsByTagName("provider");
        org.w3c.dom.Element shizuku = null;
        for (int i = 0; i < providers.getLength(); i++) {
            var element = (org.w3c.dom.Element) providers.item(i);
            if ("rikka.shizuku.ShizukuProvider".equals(element.getAttribute("android:name"))) shizuku = element;
        }
        check(shizuku != null && (Protocol.MODULE + ".shizuku").equals(shizuku.getAttribute("android:authorities"))
                        && "true".equals(shizuku.getAttribute("android:exported")) && "false".equals(shizuku.getAttribute("android:multiprocess"))
                        && shizuku.getAttribute("android:process").isEmpty()
                        && "android.permission.INTERACT_ACROSS_USERS_FULL".equals(shizuku.getAttribute("android:permission")),
                "official provider initializes Sui in the broker process with its protected authority");
    }
    static void topInsetTests() {
        check(DisplaySettings.defaults().topInset == 40 && new DisplaySettings(860, 480, 160).topInset == 0,
                "new defaults reserve 40 rows while explicit unpadded settings keep zero");
        DisplaySettings padded = new DisplaySettings(860, 480, 160, 40);
        check(padded.width == 860 && padded.height == 480 && padded.dpi == 160 && padded.frameHeight() == 520,
                "black band adds output rows without shrinking the app display or changing its DPI");
        check(DisplaySettings.defaults().frameHeight() == 480, "440 app rows plus 40 default band rows produce 480 output rows");
        check(new DisplaySettings(860, 480, 160, 479).frameHeight() == 959, "large valid band still preserves every app row");
        rejects(() -> new DisplaySettings(860, 480, 160, -1), "negative top band rejected");
        rejects(() -> new DisplaySettings(860, 480, 160, 480), "band height remains bounded below app height");
        rejects(() -> new DisplaySettings(860, 480, 160, Integer.MAX_VALUE), "oversized top band rejected");

        // Read-only, offset, pixel-strided source: the last row has no trailing padding.
        byte[] image = {88,88,88,88,
                1,2,3,4,99,99,99,99,5,6,7,8,99,99,99,99,
                9,10,11,12,99,99,99,99,13,14,15,16,99,99,99,99,
                17,18,19,20,99,99,99,99,21,22,23,24};
        ByteBuffer source = ByteBuffer.wrap(image).asReadOnlyBuffer(); source.position(4);
        ByteBuffer output = ByteBuffer.allocateDirect(40);
        byte[] shifted = {0,0,0,(byte)255,0,0,0,(byte)255,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24};
        for (java.nio.ByteOrder order : new java.nio.ByteOrder[]{java.nio.ByteOrder.BIG_ENDIAN, java.nio.ByteOrder.LITTLE_ENDIAN}) {
            output.order(order);
            PixelPacking.rgba(source, 16, 8, 2, 3, output, 1);
            byte[] actual = new byte[output.remaining()]; output.duplicate().get(actual);
            check(Arrays.equals(actual, shifted), "opaque black row precedes every unchanged source row, including the last pixel");
            check(output.position() == 0 && output.limit() == 32 && output.order() == order, "expanded frame stays readable with its byte order intact");
            check(source.position() == 4 && source.limit() == image.length, "black band does not consume the input buffer");
        }
        PixelPacking.rgba(source, 16, 8, 2, 3, output, 2);
        byte[] large = new byte[output.remaining()]; output.duplicate().get(large);
        check(Arrays.equals(large, new byte[]{0,0,0,(byte)255,0,0,0,(byte)255,0,0,0,(byte)255,0,0,0,(byte)255,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24}),
                "increasing band height never discards source rows");
        PixelPacking.rgba(source, 16, 8, 2, 3, output);
        byte[] plain = new byte[output.remaining()]; output.duplicate().get(plain);
        check(Arrays.equals(plain, new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24}),
                "disabling the band exposes only the original rows without stale padding");
        ByteBuffer dense = ByteBuffer.wrap(new byte[]{1,2,3,4,5,6,7,8});
        ByteBuffer small = ByteBuffer.allocate(12);
        PixelPacking.rgba(dense, 4, 4, 1, 2, small, 1);
        check(Arrays.equals(small.array(), new byte[]{0,0,0,(byte)255,1,2,3,4,5,6,7,8}), "contiguous RGBA copy shifts both source rows without cropping");
        rejects(() -> PixelPacking.rgba(source, 16, 8, 2, 3, ByteBuffer.allocate(24), 1), "destination sized for the old cropped frame is rejected");
        ByteBuffer truncated = source.duplicate(); truncated.limit(source.limit() - 1);
        rejects(() -> PixelPacking.rgba(truncated, 16, 8, 2, 3, output, 1), "missing bottom pixel cannot produce a partial padded frame");
        rejects(() -> PixelPacking.rgba(source, 16, 8, 2, 3, output, -1), "negative packed offset rejected");
        rejects(() -> PixelPacking.rgba(source, 16, 8, 2, 3, output, Integer.MAX_VALUE), "overflow-sized packed offset is rejected before writing");

        // The displayed point includes the top band before phone rotation/FIT; injection must undo both.
        for (boolean rotate : new boolean[]{false, true}) for (int[] view : new int[][]{{360,740}, {360,320}, {1200,540}}) {
            PreviewTransform mapping = new PreviewTransform(860, 480, view[0], view[1], rotate, 40);
            for (float[] sourcePoint : new float[][]{{.5f,.5f}, {859.5f,.5f}, {.5f,479.5f}, {859.5f,479.5f}, {430,240}}) {
                float displayedY = sourcePoint[1] + 40;
                float x = mapping.fit.left + (rotate ? 520 - displayedY : sourcePoint[0]) / mapping.fit.scale;
                float y = mapping.fit.top + (rotate ? sourcePoint[0] : displayedY) / mapping.fit.scale;
                check(mapping.contains(x, y), "all app corners and centre stay touchable below the extra band after FIT and rotation");
                mapped(mapping.input, x, y, sourcePoint[0], sourcePoint[1]);
            }
            float bandX = mapping.fit.left + (rotate ? 500 : 430) / mapping.fit.scale;
            float bandY = mapping.fit.top + (rotate ? 430 : 20) / mapping.fit.scale;
            check(!mapping.contains(bandX, bandY), "top band rejects a touch in either preview orientation");
            check(!mapping.contains(mapping.fit.left - 1, mapping.fit.top), "outer FIT margin remains noninteractive");
        }
        PreviewTransform largeBand = new PreviewTransform(860, 480, 860, 959, false, 479);
        check(!largeBand.contains(430, 478.5f) && largeBand.contains(430, 479.5f), "large band starts the complete app exactly at the lower boundary");
        mapped(largeBand.input, 430, 479.5f, 430, .5f);
        check(largeBand.contains(430, 958.5f), "bottom app row remains interactive with a large band");
        mapped(largeBand.input, 430, 958.5f, 430, 479.5f);
        rejects(() -> new PreviewTransform(860, 480, 360, 740, true, -1), "negative preview offset rejected");
        rejects(() -> new PreviewTransform(860, Integer.MAX_VALUE, 360, 740, false, 1), "overflow-sized padded preview is rejected");
    }
    public static void main(String[] args) throws Exception {
        AuthorizationTests.run();
        VehicleStartTests.run();
        BandColorTests.run();
        EncodingTests.run();
        FramePacerTests.run();
        themeTests();
        componentContractTests();
        topInsetTests();
        check(!PrivilegeMode.ROOT.useShizuku(true), "explicit Root keeps its chosen backend");
        check(PrivilegeMode.AUTO.useShizuku(true) && !PrivilegeMode.AUTO.useShizuku(false), "automatic backend prefers authorized Shizuku only");
        check(PrivilegeMode.SHIZUKU.useShizuku(true), "explicit authorized Shizuku selected");
        boolean unavailable=false;try{PrivilegeMode.SHIZUKU.useShizuku(false);}catch(IllegalStateException e){unavailable=true;}
        check(unavailable,"explicit Shizuku never silently falls back to su");
        rejects(()->PrivilegeMode.parse("COMMAND"),"unknown privilege mode rejected");
        byte[] rtp={(byte)0x80,(byte)0xe0,0,1,0,0,0,100,0,0,0,1,42};
        ByteBuffer packet=ByteBuffer.wrap(rtp);RtpPacket parsed=RtpPacket.parse(packet);
        check(parsed!=null&&parsed.marker&&parsed.bytes==13&&parsed.frameKey.equals("1:100"),"RTP marker, length and frame timestamp parsed");
        check(packet.position()==0&&packet.limit()==13,"RTP observation leaves caller buffer unchanged");
        ByteBuffer offset=ByteBuffer.allocateDirect(32);offset.position(4);offset.put(rtp);offset.limit(17);offset.position(4);
        check(RtpPacket.parse(offset.asReadOnlyBuffer()).bytes==13&&offset.position()==4,"RTP supports direct/read-only sliced payload");
        check(RtpPacket.parse(ByteBuffer.wrap(new byte[11]))==null,"truncated RTP rejected");
        byte[] control=rtp.clone();control[1]=(byte)200;check(RtpPacket.parse(ByteBuffer.wrap(control))==null,"RTCP is not counted as video");
        byte[] version=rtp.clone();version[0]=0x40;check(RtpPacket.parse(ByteBuffer.wrap(version))==null,"wrong RTP version rejected");
        byte[] csrc=rtp.clone();csrc[0]=(byte)0x83;check(RtpPacket.parse(ByteBuffer.wrap(csrc))==null,"truncated CSRC header rejected");
        byte[] extension=rtp.clone();extension[0]=(byte)0x90;check(RtpPacket.parse(ByteBuffer.wrap(extension))==null,"truncated RTP extension rejected");
        byte[] padding=rtp.clone();padding[0]=(byte)0xa0;padding[12]=0;check(RtpPacket.parse(ByteBuffer.wrap(padding))==null,"invalid RTP padding rejected");
        StreamStats stats=new StreamStats(1000);stats.captured(1100);stats.captured(1500);stats.replaced();stats.encoded(1500,"encoder",500);
        stats.encoded(1500,"nestedEncoder",900);stats.packet(1500,"sender",13,"1:100",true);stats.packet(1500,"sender",13,"1:100",true);
        stats.packet(1500,"nestedSender",99,"1:101",true);
        StreamStats.Snapshot snapshot=stats.snapshot(2000);
        check(snapshot.captured()==2&&snapshot.replaced()==1,"capture and encoder supply count independently");
        check(snapshot.encoded()==1&&snapshot.encodedBytes()==500,"single encoder callback source prevents nested double count");
        check(snapshot.packets()==2&&snapshot.sentBytes()==26&&snapshot.sentFrames()==1,"repeated RTP submissions count bytes but deduplicate same frame end");
        close((float)snapshot.captureFps(),2,"capture rate uses elapsed session time");close((float)snapshot.encodeBps(),4000,"bitrate uses bits per second");
        close((float)stats.snapshot(4000).sendBps(),0,"stalled sender ages out of rolling rate");
        stats.stop(4500);stats.captured(4600);stats.packet(4600,"sender",100,"1:102",true);
        check(stats.snapshot(9000).durationMs()==3500&&stats.snapshot(9000).captured()==2&&stats.snapshot(9000).sentBytes()==26,"closed session rejects late samples and freezes duration");
        check(new StreamStats(5000).snapshot(5000).sentBytes()==0,"new session resets totals");
        // Phone-only rotation: center and all four sides must map back to the original RGBA
        // buffer, including resized preview space above the phone's keyboard.
        PreviewTransform turned = new PreviewTransform(860, 480, 480, 1000, true);
        close(turned.fit.top, 70, "turned preview vertical FIT margin");
        check(!turned.fit.contains(240, 69) && !turned.fit.contains(240, 930), "turned preview bars reject new touches");
        mapped(turned.input, 240, 500, 430, 240);
        mapped(turned.input, 0, 70, 0, 480);
        mapped(turned.input, 480, 70, 0, 0);
        mapped(turned.input, 0, 930, 860, 480);
        mapped(turned.input, 480, 930, 860, 0);
        for (boolean rotate : new boolean[]{false, true}) for (int[] view : new int[][]{{360,740}, {360,320}, {1200,540}}) {
            PreviewTransform mapping = new PreviewTransform(860, 480, view[0], view[1], rotate);
            for (float[] point : new float[][]{{0,0}, {860,480}, {127,371}, {430,240}}) {
                float screenX = mapping.fit.left + (rotate ? 480 - point[1] : point[0]) / mapping.fit.scale;
                float screenY = mapping.fit.top + (rotate ? point[0] : point[1]) / mapping.fit.scale;
                mapped(mapping.input, screenX, screenY, point[0], point[1]);
            }
        }
        KeyboardPolicy.text("高德地图😀 A\n"); KeyboardPolicy.text("a".repeat(2048));
        rejects(() -> KeyboardPolicy.text("a".repeat(2049)), "input Binder text bound");
        rejects(() -> KeyboardPolicy.text(null), "missing input text rejected");
        rejects(() -> KeyboardPolicy.text("\ud83d"), "unpaired high surrogate rejected");
        rejects(() -> KeyboardPolicy.text("\ude00"), "unpaired low surrogate rejected");
        rejects(() -> KeyboardPolicy.text("\ud83dA"), "broken emoji rejected");
        KeyboardPolicy.deletion(128, 0); KeyboardPolicy.deletion(64, 64);
        rejects(() -> KeyboardPolicy.deletion(-1, 0), "negative deletion rejected");
        rejects(() -> KeyboardPolicy.deletion(128, 1), "combined deletion bound");
        rejects(() -> KeyboardPolicy.deletion(Integer.MAX_VALUE, Integer.MAX_VALUE), "deletion overflow rejected");
        for (int key : new int[]{7,16,19,22,29,54,59,61,62,66,67,76,112,113,122,123}) check(KeyboardPolicy.key(key), "editing key allowed");
        for (int key : new int[]{0,3,4,5,6,24,25,26,64,65,84,117,118,187,219,279,284}) check(!KeyboardPolicy.key(key), "system/clipboard key denied on public typing endpoint");
        // Complete portrait phone content must fit within a landscape dash without cropping.
        float[] portrait = Geometry.fit(1080, 2400, 800, 480);
        close(portrait[0], 292, "portrait left pillarbox"); close(portrait[2], 508, "portrait right pillarbox");
        close(portrait[1], 0, "portrait not vertically cropped"); close(portrait[3], 480, "portrait full height");
        // Folded/unfolded and rotated source geometries stay within capture limits and remain even.
        for (int[] input : new int[][] {{1080,2400}, {2480,2200}, {2200,2480}, {2400,1080}, {1,1}}) {
            int[] size = Geometry.captureSize(input[0], input[1], 720);
            check(size[0] % 2 == 0 && size[1] % 2 == 0, "even capture dimensions");
            check(Math.max(size[0], size[1]) <= 720 && Math.min(size[0], size[1]) >= 2, "bounded capture dimensions");
        }
        rejects(() -> Geometry.captureSize(0, 20, 720), "invalid physical display rejected");
        rejects(() -> Geometry.fit(1, 1, 0, 1), "zero target rejected");
        // Two rows, each padded to 12 bytes, but the final row has no accessible trailing padding.
        byte[] padded = {1,2,3,4,5,6,7,8,99,99,99,99,9,10,11,12,13,14,15,16};
        ByteBuffer source = ByteBuffer.wrap(padded), output = ByteBuffer.allocate(16);
        PixelPacking.rgba(source, 12, 4, 2, 2, output);
        byte[] expected = new byte[16]; for (int i = 0; i < 16; i++) expected[i] = (byte)(i + 1);
        check(Arrays.equals(expected, output.array()), "row padding is excluded without reading beyond limit");
        check(source.position() == 0, "input position is not consumed");
        rejects(() -> PixelPacking.rgba(ByteBuffer.wrap(new byte[19]),12,4,2,2,output), "short last row rejected");
        rejects(() -> PixelPacking.rgba(source, 12, 4, 2, 2, ByteBuffer.allocate(15)), "undersized destination rejected");
        byte[] spaced = {1,2,3,4,99,99,99,99,5,6,7,8};
        ByteBuffer twoPixels = ByteBuffer.allocate(8);
        PixelPacking.rgba(ByteBuffer.wrap(spaced),16,8,2,1,twoPixels);
        check(Arrays.equals(twoPixels.array(), new byte[]{1,2,3,4,5,6,7,8}), "pixel stride handled");
        // Exporting a Binder service must not make the user's screen public to unrelated applications.
        check(!CallerPolicy.allowed(20001,20002,null), "unknown caller denied");
        check(!CallerPolicy.allowed(20001,20002,new String[]{"evil.cn.ninebot.ninebot"}), "lookalike package denied");
        check(!CallerPolicy.allowed(20001,20002,new String[]{"com.example.other"}), "unrelated app denied");
        check(CallerPolicy.allowed(20001,20002,new String[]{"cn.ninebot.ninebot"}), "intended target allowed");
        check(CallerPolicy.allowed(20002,20002,null), "own application allowed");
        // Hooks must remain out of authentication, transport and vehicle command implementations.
        check(HookPolicy.captureClass("cn.ninebot.capture.codec.BitmapToH264Encoder"), "image encoder eligible");
        check(!HookPolicy.captureClass("cn.ninebot.nbcrypto.NbEncryption"), "crypto excluded");
        check(!HookPolicy.captureClass("cn.ninebot.library.nbbluetooth.protocol.BleProtocol"), "BLE excluded");
        check(!HookPolicy.captureClass("cn.ninebot.capture.R$drawable"), "resources excluded");
        check(HookPolicy.coroutineDrawingMethod("cn.ninebot.capture.ViewToBitmapConvert$convert$1", "invokeSuspend"), "async drawing eligible");
        // A cancelled/stale Android activity result must never trigger another vehicle session.
        String first = "0123456789abcdef0123456789abcdef", second = "fedcba9876543210fedcba9876543210";
        DirectSession direct = new DirectSession();
        check(!direct.begin("invalid"), "malformed direct request rejected");
        check(!Protocol.validRequest(null) && !Protocol.validRequest(first + "0"), "missing/oversized request rejected");
        check(direct.begin(first), "user gesture starts vehicle checks");
        check(!direct.begin(second), "double tap cannot replace pending consent");
        check(!direct.launch(first), "cannot launch before consent");
        check(!direct.granted(second), "foreign consent result ignored");
        check(!direct.granted(first), "vehicle checks precede service consent");
        check(direct.powerChecked(first, true) && direct.cruiseReady(first) && direct.prepareDisplay(first), "powered vehicle and original cruise accepted before allocation");
        check(direct.granted(first), "matching consent result accepted");
        check(!direct.granted(first), "duplicate consent result ignored");
        check(direct.launch(first), "first launch accepted after consent");
        check(!direct.launch(first), "duplicate launch rejected");
        check(direct.running(first), "observed frames confirm running phase");
        check(!direct.end(second), "old/different stop ignored");
        check(direct.end(first) && direct.phase() == DirectSession.Phase.IDLE, "stop returns to idle");
        check(direct.begin(second) && !direct.granted(first), "late old result cannot authorize new request");
        check(direct.end(second) && !direct.launch(second), "cancel before launch never dispatches cruise");
        // A local preview owns one display without ever becoming authorized to launch a cruise.
        check(!direct.begin(first, null), "missing session mode rejected");
        check(direct.begin(first, DirectSession.Mode.LOCAL) && direct.isLocal(), "local preview starts independently");
        check(!direct.localReady(first) && !direct.launch(first), "local preview waits for service and frames");
        check(!direct.begin(second, DirectSession.Mode.VEHICLE), "vehicle session cannot steal a pending local display");
        check(!direct.granted(second) && direct.granted(first), "local service acknowledgement is request-bound");
        check(!direct.launch(first), "local first frame never grants cruise dispatch");
        check(!direct.running(first), "encoder hooks cannot mark a local session ready");
        check(!direct.localReady(second), "old local frame callback ignored");
        check(direct.localReady(first) && direct.phase() == DirectSession.Phase.RUNNING, "local preview runs without STARTING cruise phase");
        check(!direct.localReady(first) && !direct.launch(first), "duplicate local readiness cannot start a cruise");
        check(!direct.end(second) && direct.isLocal(), "unrelated stop cannot change local session ownership");
        check(direct.end(first) && direct.mode() == null, "local exit clears its mode and lease");
        check(direct.begin(second) && !direct.isLocal(), "normal cast still defaults to vehicle mode after local preview");
        check(direct.powerChecked(second, true) && direct.cruiseReady(second) && direct.prepareDisplay(second), "new vehicle attempt must repeat native checks");
        check(direct.granted(second) && !direct.localReady(second), "local callback cannot bypass native cruise readiness");
        check(direct.launch(second) && direct.running(second), "normal vehicle path still requires cruise launch and capture");
        check(!direct.begin(first, DirectSession.Mode.LOCAL), "local mode cannot replace a running vehicle cast");
        check(direct.end(second) && direct.begin(first, DirectSession.Mode.LOCAL), "local mode can start after vehicle cleanup");
        check(direct.end(first) && !direct.granted(first) && !direct.localReady(first), "cancelled local startup rejects late callbacks");
        check(HookPolicy.interestingClass("cn.ninebot.device.motor.navi.CruiseModeActivity"), "known cruise lifecycle is observable");
        check(!HookPolicy.captureClass("cn.ninebot.device.motor.navi.CruiseModeActivity"), "cruise methods are trace-only");
        // Cancellation before/after Root attach and stale process callbacks are the key leak boundaries.
        SessionLease lease = new SessionLease();
        check(lease.begin(first, second), "root lease begins");
        check(!lease.ready(second) && !lease.isReady(), "cannot mark ready without handshake");
        check(!lease.attach(first), "wrong bootstrap nonce rejected");
        check(lease.attach(second) && !lease.attach(second), "nonce can be consumed only once");
        check(lease.ready(second) && lease.isReady(), "attached process may publish display");
        check(!lease.end(second) && lease.owns(first), "foreign stop leaves display alive");
        check(lease.end(first) && !lease.isReady() && !lease.authorize(second), "stop revokes root endpoint and display readiness");
        check(lease.begin(second, first) && !lease.attach(second), "stale process cannot attach to later session");
        check(!lease.end(first) && lease.owns(second), "old death callback cannot stop new display");
        check(lease.end(second) && !lease.attach(first), "cancel before su approval rejects late bootstrap");
        check(!lease.begin(null, first) && !lease.begin(first, "wrong"), "malformed nonce rejected");
        // Resolution allocations and density must be checked before opening a Surface.
        DisplaySettings defaults = DisplaySettings.defaults(); check(defaults.width == 848 && defaults.height == 440 && defaults.dpi == 160, "default dash layout is 848x440/160 DPI");
        new DisplaySettings(1920, 1080, 320); new DisplaySettings(480, 800, 160);
        rejects(() -> new DisplaySettings(Integer.MAX_VALUE, 480, 160), "overflow-sized width rejected");
        rejects(() -> new DisplaySettings(1920, 1920, 160), "excessive frame memory rejected");
        rejects(() -> new DisplaySettings(801, 480, 160), "odd size rejected");
        rejects(() -> new DisplaySettings(800, 480, 0), "invalid dpi rejected");
        rejects(() -> new DisplaySettings(320, 320, 480), "unusable dp dimensions rejected");
        check(DisplaySettings.shellQuote("a'b$()\n").equals("'a'\\''b$()\n'"), "APK path shell quoting is literal");
        TouchMapping touch = new TouchMapping(800, 480, 1000, 1000);
        check(!touch.contains(500, 100) && touch.contains(500, 500), "preview bars do not inject touches");
        close(touch.x(500), 400, "preview center x"); close(touch.y(500), 240, "preview center y");
        close(touch.x(0), 0, "preview left edge"); close(touch.y(200), 0, "preview top edge");
        TouchMapping portraitPreview = new TouchMapping(800, 480, 480, 800);
        close(portraitPreview.x(240), 400, "rotation keeps target center x"); close(portraitPreview.y(400), 240, "rotation keeps target center y");
        check(!portraitPreview.contains(480, 400), "right boundary is exclusive");
        check(HookPolicy.bitmapGetter("cn.ninebot.capture.encoder.ViewEncoder", "getBitmap", 0, true), "synchronous getter eligible for early frame");
        check(!HookPolicy.bitmapGetter("cn.ninebot.capture.encoder.ViewEncoder$1", "invokeSuspend", 1, false), "never short circuit coroutine state machine");
        check(!HookPolicy.bitmapGetter("cn.ninebot.capture.encoder.ViewEncoder", "getBitmap", 1, true), "parameterized conversion keeps side effects");
        check(!HookPolicy.bitmapGetter("cn.ninebot.capture.encoder.ViewEncoder", "run", 0, true), "timer callback is never skipped");
        check(HookPolicy.terminalCastMethod("cn.ninebot.mapcapture.DeviceScreenCastManager", "stopScreenCast"), "cast termination recognized");
        check(!HookPolicy.terminalCastMethod("cn.ninebot.capture.codec.CodecEncoder", "release"), "encoder recreation is not cruise termination");
        // Inline preview follows the map region, including inset/clipped layouts on a foldable.
        check(Arrays.equals(InlineBounds.clip(0, 24, 1080, 2200, 1080, 2400), new int[]{0,24,1080,2200}), "map region preserves top inset");
        check(Arrays.equals(InlineBounds.clip(-20, -40, 820, 520, 800, 480), new int[]{0,0,800,480}), "partly offscreen map is clipped");
        check(InlineBounds.clip(800, 0, 100, 100, 800, 480) == null, "offscreen map cannot create an input layer");
        check(InlineBounds.clip(0, 0, 0, 480, 800, 480) == null, "unlaid out map is retried");
        check(InlineBounds.clip(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 480, 800, 480) == null, "overflowing map extent cannot wrap onto host");
        TouchMapping embedded = new TouchMapping(800,480,480,752); // 800-high content minus 48-high toolbar.
        close(embedded.x(240), 400, "embedded touch uses picture width");
        close(embedded.y(376), 240, "embedded touch excludes toolbar from picture geometry");
        check(!embedded.contains(240, 10), "embedded letterbox cannot start a gesture");
        check(CallerPolicy.controls(20001, 20001, true), "current Ninebot owner can control ready display");
        check(!CallerPolicy.controls(20002, 20001, true), "another allowed package cannot control owner session");
        check(!CallerPolicy.controls(20001, 20001, false), "cancelled or stale display rejects input even from original owner");
        check(!CallerPolicy.controls(0, 0, true), "uninitialized owner UID cannot grant control");
        // A bind request is not a connection; absent callbacks must eventually retire that binding.
        BindingState binding = new BindingState();
        long initial = binding.begin(1000);
        check(!binding.expired(5999) && binding.expired(6000), "missing callback expires at five seconds");
        long replacement = binding.begin(6000);
        check(!binding.connected(initial) && !binding.current(initial), "late connection from retired binding rejected");
        check(!binding.disconnected(initial, 10000), "late disconnect cannot reset replacement's deadline");
        check(binding.expired(11000), "stale callbacks cannot leave binding stuck indefinitely");
        check(binding.connected(replacement) && !binding.expired(99999), "live binding has no retry deadline");
        check(binding.disconnected(replacement, 100000), "live binding death starts a fresh grace period");
        check(!binding.expired(104999), "short disconnect does not immediately tear down the binding");
        check(binding.disconnected(replacement, 104000) && binding.expired(105000), "duplicate death events cannot postpone retry forever");
        check(binding.connected(replacement) && !binding.expired(110000), "framework reconnect during grace restores live binding");
        long newest = binding.begin(120000);
        check(binding.connected(newest), "new replacement can connect");
        check(!binding.disconnected(replacement, 121000) && !binding.expired(130000), "old disconnect cannot invalidate a new live connection");
        // A lost STOP must survive reconnection and a cancellation racing a late BEGIN acknowledgement.
        PendingStops stops = new PendingStops();
        stops.add(null); stops.add("invalid");
        check(stops.size() == 0 && stops.first() == null, "invalid cancellation cannot enter retry queue");
        stops.add(first); PendingStops.Entry inFlight = stops.first();
        check(inFlight.request.equals(first), "offline cancellation retained until acknowledged");
        stops.add(first);
        check(stops.size() == 1, "repeated cancellation coalesces by session");
        stops.acknowledged(inFlight);
        check(stops.size() == 1 && stops.first() != inFlight, "ack from STOP before late BEGIN cannot erase renewed cancellation");
        stops.add(second); PendingStops.Entry latestStop = stops.first();
        stops.acknowledged(latestStop);
        check(stops.size() == 1 && stops.first().request.equals(second), "ack affects only the matching cancellation");
        stops.acknowledged(latestStop);
        check(stops.size() == 1, "duplicate old ack cannot remove the next session's stop");
        stops.acknowledged(stops.first());
        check(stops.size() == 0 && stops.first() == null, "all acknowledged cancellations leave an empty queue");
        stops.add(first);
        check(stops.first().request.equals(first), "cancellation may be requeued even after an earlier ack");
        // A chat may truncate the pasted report; signature discovery must not hide the latest failure.
        String noisy = "12:00 MODULE loaded\n12:01 BRIDGE connected\n12:02 VD STATUS active=false\n";
        for (int i = 0; i < 300; i++) noisy += "12:03 SIGNATURE long.method." + i + "\n";
        noisy += "12:04 DIRECT ENTRY cardShown=true\n12:04 STAT replaced=0\n12:05 BRIDGE module process pid=99\n";
        String digest = LogDigest.recent(noisy, 120);
        check(digest.startsWith("12:05 BRIDGE module process pid=99"), "newest process transition leads the summary");
        check(digest.contains("VD STATUS active=false"), "failure survives hundreds of verbose signatures");
        check(!digest.contains("SIGNATURE") && !digest.contains("DIRECT ENTRY") && !digest.contains("STAT replaced"), "routine discovery and counters cannot crowd out failures");
        check(digest.length() <= 120, "pasted event digest obeys character budget");
        check(LogDigest.recent("x".repeat(2000), 100).length() == 100, "single long failure remains bounded");
        check(LogDigest.recent("old\r\nnew", 20).equals("new\nold"), "Windows line endings preserve newest-first order");
        check(LogDigest.recent(null, 20).isEmpty() && LogDigest.recent("message", 0).isEmpty(), "empty diagnostic inputs are valid");
        check(LogDigest.head("ok😀later", 4).equals("ok…"), "clipping cannot split a surrogate pair");
        check(LogDigest.head("unchanged", 20).equals("unchanged"), "short diagnostic text is preserved");
        // Fixed Surface pixels must reach the same visual targets in all four logical rotations.
        mapped(DisplayInputTransform.matrix(0, 800, 480, 800, 480), 100, 80, 100, 80);
        mapped(DisplayInputTransform.matrix(1, 800, 480, 480, 800), 100, 80, 80, 700);
        mapped(DisplayInputTransform.matrix(2, 800, 480, 800, 480), 100, 80, 700, 400);
        mapped(DisplayInputTransform.matrix(3, 800, 480, 480, 800), 100, 80, 400, 100);
        mapped(DisplayInputTransform.matrix(1, 800, 480, 480, 800), 700, 400, 400, 100);
        mapped(DisplayInputTransform.matrix(3, 800, 480, 480, 800), 700, 400, 80, 700);
        mapped(DisplayInputTransform.matrix(1, 480, 800, 800, 480), 80, 100, 100, 400);
        mapped(DisplayInputTransform.matrix(3, 480, 800, 800, 480), 80, 100, 700, 80);
        mapped(DisplayInputTransform.matrix(1, 800, 480, 960, 1600), 100, 80, 160, 1400);
        for (int r = 0; r < 4; r++) {
            boolean odd = (r & 1) != 0;
            mapped(DisplayInputTransform.matrix(r, 800, 480, odd ? 480 : 800, odd ? 800 : 480),
                    400, 240, odd ? 240 : 400, odd ? 400 : 240);
        }
        rejects(() -> DisplayInputTransform.matrix(-1, 800, 480, 480, 800), "negative rotation rejected");
        rejects(() -> DisplayInputTransform.matrix(4, 800, 480, 480, 800), "unknown rotation rejected");
        rejects(() -> DisplayInputTransform.matrix(0, 0, 480, 480, 800), "empty Surface rejected");
        rejects(() -> DisplayInputTransform.matrix(0, 800, 480, 480, 0), "removed logical display rejected");
        // A task moved to the phone leaves an empty display; transitions/errors must not become false recovery prompts.
        AppRecoveryState recovery = new AppRecoveryState();
        recovery.started(0);
        recovery.sample(0, false); recovery.sample(2999, false);
        check(recovery.state() == AppRecoveryState.HIDDEN, "first app launch gets a grace period");
        check(!recovery.restart(2999), "cannot relaunch before an empty display is confirmed");
        recovery.sample(4000, false); recovery.sample(5199, false);
        check(recovery.state() == AppRecoveryState.HIDDEN, "a short empty transition does not show recovery");
        recovery.sample(5200, false);
        check(recovery.state() == AppRecoveryState.MISSING, "persistent empty display offers restart");
        check(recovery.restart(5300) && recovery.state() == AppRecoveryState.RESTARTING, "explicit restart starts one recovery attempt");
        check(!recovery.restart(5301), "double tap cannot launch a second recovery attempt");
        recovery.sample(8300, false); recovery.sample(9499, false);
        check(recovery.state() == AppRecoveryState.RESTARTING, "keep pending UI until task absence is confirmed again");
        recovery.sample(9500, false);
        check(recovery.state() == AppRecoveryState.MISSING, "failed task return offers another attempt");
        check(recovery.restart(9600), "retry accepted after failed recovery");
        recovery.sample(9700, true);
        check(recovery.state() == AppRecoveryState.HIDDEN, "returning app immediately removes recovery UI");
        check(!recovery.restart(9701), "late click cannot relaunch an app already back on the display");
        recovery.sample(14000, false); recovery.sample(14500, true); recovery.sample(15000, false);
        recovery.sample(16199, false);
        check(recovery.state() == AppRecoveryState.HIDDEN, "brief occupancy resets the consecutive-empty interval");
        recovery.unknown(); recovery.sample(17000, false); recovery.sample(18199, false);
        check(recovery.state() == AppRecoveryState.HIDDEN, "failed task queries do not count as empty samples");
        recovery.sample(18200, false);
        check(recovery.state() == AppRecoveryState.MISSING, "fresh empty samples recover after a query failure");
        recovery.unknown();
        check(recovery.state() == AppRecoveryState.HIDDEN, "unknown task state hides a stale prompt");
        recovery.failed();
        check(recovery.state() == AppRecoveryState.MISSING, "launch exception keeps retry available");
        recovery.started(20000);
        check(recovery.state() == AppRecoveryState.HIDDEN && !recovery.restart(20000), "new display does not inherit previous recovery state");
        recovery.sample(30000, true); recovery.sample(60000, true);
        check(recovery.state() == AppRecoveryState.HIDDEN, "occupied task stays usable regardless of black or unchanged pixels");
        ProjectionTests.run();
        LogExportTests.run();
        System.out.println("PASS: " + assertions + " assertions (RGBA/top band, projection consent/size, input/rotation, lease/stop cancellation, settings, hook scope, ownership, service reconnection, statistics and app recovery)");
    }
    private static void themeTests() {
        check(ThemeMode.dark(true, null, 0xff989da8), "night mode is not overridden by secondary grey text");
        check(ThemeMode.dark(true, 0xfffafbfc, 0xff20232a), "night configuration wins over stale pre-update light views");
        check(ThemeMode.dark(true, null, null), "empty night view is dark");
        check(ThemeMode.dark(false, 0xff17191f, 0xff989da8), "custom dark host container is recognized in a day configuration");
        check(!ThemeMode.dark(false, 0xfffafbfc, 0xffffffff), "white button text cannot override the light host surface");
        check(!ThemeMode.dark(false, null, 0xff989da8), "ambiguous grey alone does not force a dark day theme");
        check(ThemeMode.dark(false, null, 0xfff0f1f4), "bright neutral host text remains a dark-theme fallback");
        check(!ThemeMode.dark(false, null, 0xff20232a), "dark host text selects light mode");
        check(!ThemeMode.dark(false, null, null), "empty day view remains light");
        check(ThemeMode.surfaceDark(0x0017191f) == null, "transparent black cannot masquerade as a dark surface");
        check(ThemeMode.surfaceDark(0x8017191f) == null, "dim overlays are not theme evidence");
        check(ThemeMode.surfaceDark(0xff92caff) == null, "accent blue does not determine the host theme");
        check(ThemeMode.surfaceDark(0xff989da8) == null, "mid-grey surfaces are ambiguous");
        check(Boolean.TRUE.equals(ThemeMode.surfaceDark(0xff000000)), "black bar background selects light icons");
        check(Boolean.FALSE.equals(ThemeMode.surfaceDark(0xffffffff)), "white bar background selects dark icons");
    }
}
