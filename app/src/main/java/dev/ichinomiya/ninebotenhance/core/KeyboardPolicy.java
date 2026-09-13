package dev.ichinomiya.ninebotenhance.core;

/** Bounded text editing commands, not a general-purpose hardware/system-key endpoint. */
public final class KeyboardPolicy {
    public static void text(String value) {
        if (value == null || value.length() > 2048) throw new IllegalArgumentException("输入文字过长");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("无效的文字编码");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("无效的文字编码");
        }
    }
    public static boolean key(int code) {
        return code >= 7 && code <= 16 || code >= 19 && code <= 22 || code >= 29 && code <= 62
                || code >= 66 && code <= 77 || code == 111 || code == 112 || code == 113 || code == 114
                || code == 115 || code == 122 || code == 123;
    }
    public static void deletion(int before, int after) {
        if (before < 0 || after < 0 || before > 128 || after > 128 || before + after > 128)
            throw new IllegalArgumentException("单次删除范围过大");
    }
    private KeyboardPolicy() {}
}
