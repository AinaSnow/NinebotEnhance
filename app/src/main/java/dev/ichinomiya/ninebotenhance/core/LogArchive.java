package dev.ichinomiya.ninebotenhance.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Private staging files become immutable snapshots before any read grant is issued. */
public final class LogArchive {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final long MAX_AGE_MS = 24 * 60 * 60 * 1000L;
    private static final long DRAFT_AGE_MS = 60 * 1000L;
    private final File directory;
    private final Consumer<File> removed;
    private final Map<String, Draft> drafts = new HashMap<>();
    public record Ticket(String token, File file) {}
    private record Draft(int uid, long created, File file) {}
    public LogArchive(File directory) { this(directory, file -> {}); }
    public LogArchive(File directory, Consumer<File> removed) { this.directory = directory; this.removed = removed; }
    public static boolean validName(String name) {
        return name != null && name.matches("NinebotEnhance-[a-f0-9]{32}\\.txt");
    }
    public synchronized Ticket begin(int uid, String moduleLog, long now) throws IOException {
        if (uid < 10000) throw new SecurityException("日志调用方无效");
        Files.createDirectories(directory.toPath()); clean(now, null);
        if (drafts.size() >= 4) throw new IOException("日志请求过多，请稍后重试");
        byte[] bytes = moduleLog.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("模块日志超过文件大小限制");
        String token = UUID.randomUUID().toString().replace("-", "");
        File file = new File(directory, token + ".part");
        Files.write(file.toPath(), bytes);
        drafts.put(token, new Draft(uid, now, file));
        return new Ticket(token, file);
    }
    public synchronized File commit(int uid, String token, long now) throws IOException {
        Draft draft = drafts.get(token);
        if (draft == null || draft.uid != uid) throw new SecurityException("日志请求已失效");
        drafts.remove(token);
        File result = new File(directory, "NinebotEnhance-" + token + ".txt");
        try {
            if (now - draft.created > DRAFT_AGE_MS) throw new IOException("日志生成超时，请重试");
            // Copy rather than rename: a retained writable descriptor must not alter a published log.
            try (InputStream input = new FileInputStream(draft.file); OutputStream output = new FileOutputStream(result)) {
                byte[] buffer = new byte[16384]; int count, total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_BYTES) throw new IOException("日志超过 4 MiB，文件未分享");
                    output.write(buffer, 0, count);
                }
            }
            clean(now, result);
            return result;
        } catch (IOException | RuntimeException e) { result.delete(); throw e; }
        finally { draft.file.delete(); }
    }
    public synchronized void cancel(int uid, String token) {
        Draft draft = drafts.get(token);
        if (draft != null && draft.uid == uid) { drafts.remove(token); draft.file.delete(); }
    }
    public synchronized File resolve(String name, long now) throws IOException {
        if (!validName(name)) throw new FileNotFoundException("日志文件名无效");
        File file = new File(directory, name);
        if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())
                || !file.isFile() || now - file.lastModified() > MAX_AGE_MS)
            throw new FileNotFoundException("日志已过期，请重新分享");
        return file;
    }
    private void discard(File file) { if (file.delete()) removed.accept(file); }
    private void clean(long now, File newest) {
        drafts.entrySet().removeIf(entry -> {
            if (now - entry.getValue().created <= DRAFT_AGE_MS) return false;
            entry.getValue().file.delete(); return true;
        });
        File[] files = directory.listFiles(); if (files == null) return;
        List<File> published = new ArrayList<>();
        for (File file : files) {
            if (validName(file.getName())) {
                if (file.equals(newest)) continue;
                if (now - file.lastModified() > MAX_AGE_MS) discard(file); else published.add(file);
            } else if (file.getName().matches("[a-f0-9]{32}\\.part") && now - file.lastModified() > DRAFT_AGE_MS) file.delete();
        }
        published.sort(Comparator.comparingLong(File::lastModified).reversed());
        for (int i = newest == null ? 16 : 15; i < published.size(); i++) discard(published.get(i));
    }
}
