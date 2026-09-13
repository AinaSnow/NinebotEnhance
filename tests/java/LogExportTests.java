import dev.ichinomiya.ninebotenhance.core.LogArchive;
import dev.ichinomiya.ninebotenhance.platform.ModuleResources;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import static java.nio.file.StandardOpenOption.APPEND;

final class LogExportTests {
    private static void check(boolean condition, String message) { CoreTests.check(condition, message); }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException | SecurityException expected) { check(true, message); return; }
        check(false, message);
    }
    static void run() throws Exception {
        Path build = Path.of("build").toAbsolutePath().normalize(); Files.createDirectories(build);
        Path scratch = Files.createTempDirectory(build, "log-export-tests-");
        try {
            Set<String> removed = new HashSet<>();
            LogArchive logs = new LogArchive(scratch.toFile(), file -> removed.add(file.getName())); long now = System.currentTimeMillis();
            int owner = 10123, other = 10456;
            String module = "模块日志\n" + "连接状态已读取\n".repeat(20000);
            String local = "九号进程\n" + "画面统计与帧率😀\n".repeat(45000) + "END-OF-LOG\n";
            LogArchive.Ticket draft = logs.begin(owner, module, now);
            check(!LogArchive.validName(draft.file().getName()), "incomplete staging file has no shareable name");
            Files.writeString(draft.file().toPath(), local, StandardCharsets.UTF_8, APPEND);
            check(draft.file().length() > 1024 * 1024, "real Unicode log exceeds Binder/clipboard payload sizes");
            rejects(() -> logs.commit(other, draft.token(), now), "another caller cannot publish this draft");
            logs.cancel(other, draft.token());
            check(draft.file().exists(), "another caller cannot cancel this draft");
            File file = logs.commit(owner, draft.token(), now);
            check(Files.readString(file.toPath()).equals(module + local), "complete UTF-8 log preserves both processes, emoji and final marker");
            check(logs.resolve(file.getName(), now).equals(file), "only a published file can be resolved");
            rejects(() -> logs.commit(owner, draft.token(), now), "a publish ticket cannot be replayed");
            for (String bad : new String[]{"../" + file.getName(), "%2e%2e%2f" + file.getName(), "/" + file.getName(),
                    "..\\" + file.getName(), "C:\\secret.txt", draft.token() + ".part", "password.txt", file.getName() + "/x", file.getName() + ":stream"})
                rejects(() -> logs.resolve(bad, now), "path traversal, encoded segments, drafts and alternate streams are rejected");
            rejects(() -> logs.resolve(file.getName(), now + LogArchive.MAX_AGE_MS + 10000), "expired log cannot be shared again");
            LogArchive.Ticket immutable = logs.begin(owner, "snapshot", now);
            // POSIX permits unlinking an open descriptor; Windows may keep the staging file until close.
            try (RandomAccessFile retained = new RandomAccessFile(immutable.file(), "rw")) {
                File published = logs.commit(owner, immutable.token(), now);
                retained.seek(0); retained.write("modified".getBytes(StandardCharsets.UTF_8));
                check(Files.readString(published.toPath()).equals("snapshot"), "retained writable descriptor cannot change an already shared snapshot");
            }
            LogArchive.Ticket cancelled = logs.begin(owner, "cancel", now); logs.cancel(owner, cancelled.token());
            rejects(() -> logs.commit(owner, cancelled.token(), now), "cancelled draft is never published");
            LogArchive.Ticket late = logs.begin(owner, "late", now);
            rejects(() -> logs.commit(owner, late.token(), now + 61000), "late completion after timeout is rejected");
            LogArchive.Ticket huge = logs.begin(owner, "", now);
            try (RandomAccessFile growth = new RandomAccessFile(huge.file(), "rw")) { growth.setLength(LogArchive.MAX_BYTES + 1L); }
            rejects(() -> logs.commit(owner, huge.token(), now), "oversized export fails instead of silently truncating");
            check(!new File(scratch.toFile(), "NinebotEnhance-" + huge.token() + ".txt").exists(), "oversized partial output is removed");
            LogArchive.Ticket[] waiting = new LogArchive.Ticket[4];
            for (int i = 0; i < waiting.length; i++) waiting[i] = logs.begin(owner, "waiting", now);
            rejects(() -> logs.begin(owner, "overflow", now), "concurrent staging files are bounded");
            for (LogArchive.Ticket ticket : waiting) logs.cancel(owner, ticket.token());
            for (int i = 0; i < 22; i++) {
                LogArchive.Ticket ticket = logs.begin(owner, "retained " + i, System.currentTimeMillis());
                File newest = logs.commit(owner, ticket.token(), System.currentTimeMillis());
                check(logs.resolve(newest.getName(), System.currentTimeMillis()).exists(), "cache cleanup never deletes the newly shared file even at equal timestamps");
            }
            try (var files = Files.list(scratch)) { check(files.filter(p -> LogArchive.validName(p.getFileName().toString())).count() <= 16, "repeated sharing bounds the private log cache"); }
            check(!removed.isEmpty() && removed.stream().allMatch(LogArchive::validName), "only removed published files trigger URI grant revocation");
            var factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document manifest = factory.newDocumentBuilder().parse(new File("app/src/main/AndroidManifest.xml"));
            Element provider = null; NodeList providers = manifest.getElementsByTagName("provider");
            for (int i = 0; i < providers.getLength(); i++) {
                Element candidate = (Element) providers.item(i);
                if (".service.LogShareProvider".equals(candidate.getAttribute("android:name"))) provider = candidate;
            }
            check(provider != null && "false".equals(provider.getAttribute("android:exported"))
                    && "true".equals(provider.getAttribute("android:grantUriPermissions"))
                    && "dev.ichinomiya.ninebotenhance.logs".equals(provider.getAttribute("android:authorities")),
                    "log provider is private and shares only URI grants under the module authority");
            Path moduleApk = scratch.resolve("module.apk");
            String projectLicense = Files.readString(Path.of("LICENSE"));
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(moduleApk))) {
                zip.putNextEntry(new ZipEntry("META-INF/licenses/NinebotEnhance-Apache-2.0.txt"));
                zip.write(projectLicense.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            ModuleResources.initialize(moduleApk.toString());
            check(ModuleResources.text("META-INF/licenses/NinebotEnhance-Apache-2.0.txt").equals(projectLicense), "About reads the complete project license directly from the module APK");
            rejects(() -> ModuleResources.text("missing.txt"), "missing module resources fail without substituting host resources");
        } finally {
            ModuleResources.initialize(null);
            if (!scratch.normalize().startsWith(build)) throw new IOException("test directory escaped workspace");
            try (var paths = Files.walk(scratch)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
}
