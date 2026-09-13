package dev.ichinomiya.ninebotenhance.diagnostics;

/** Put recent session events ahead of verbose hook signatures when pasting into a length-limited chat. */
public final class LogDigest {
    public static String head(String value, int limit) {
        if (value == null || limit <= 0) return "";
        if (value.length() <= limit) return value;
        int end = limit - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
    public static String recent(String text, int limit) {
        if (text == null || limit <= 0) return "";
        String[] lines = text.split("\\r?\\n"); StringBuilder result = new StringBuilder();
        for (int i = lines.length - 1; i >= 0 && result.length() < limit; i--) {
            String line = lines[i];
            if (line.isEmpty() || line.contains(" SIGNATURE ") || line.contains(" STAT ") || line.startsWith("STAT ")
                    || line.contains(" DIRECT ENTRY ")) continue;
            if (result.length() > 0) result.append('\n');
            result.append(head(line, Math.min(380, limit - result.length())));
        }
        return result.toString();
    }
    private LogDigest() {}
}
