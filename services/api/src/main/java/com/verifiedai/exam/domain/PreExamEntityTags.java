package com.verifiedai.exam.domain;

/** RFC 9110 weak comparison for GET, after authentication and current resource resolution. */
public final class PreExamEntityTags {
    private PreExamEntityTags() {}

    public static String quote(String digest) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected SHA-256 entity digest");
        }
        return "\"" + digest + "\"";
    }

    /** Malformed or excessively large input never yields a cache hit. */
    public static boolean matches(String header, String digest) {
        String current = quote(digest);
        if (header == null || header.length() > 8192) return false;
        String value = header.trim();
        if (value.equals("*")) return true;
        boolean found = false;
        int index = 0;
        boolean needsTag = true;
        while (index < value.length()) {
            while (index < value.length() && whitespace(value.charAt(index))) index++;
            if (index == value.length()) return false;
            if (value.startsWith("W/", index)) index += 2;
            if (index >= value.length() || value.charAt(index) != '"') return false;
            int start = index++;
            while (index < value.length() && value.charAt(index) != '"') {
                char c = value.charAt(index);
                if (c < 0x21 || c == 0x7f || c > 0xff) return false;
                index++;
            }
            if (index == value.length()) return false;
            index++;
            found |= current.equals(value.substring(start, index));
            needsTag = false;
            while (index < value.length() && whitespace(value.charAt(index))) index++;
            if (index < value.length()) {
                if (value.charAt(index++) != ',') return false;
                needsTag = true;
            }
        }
        return found && !needsTag;
    }

    private static boolean whitespace(char c) { return c == ' ' || c == '\t'; }
}
