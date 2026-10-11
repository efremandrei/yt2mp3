package com.andre.yt2mp3;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class OutputName {
    private OutputName() { }

    static String custom(String input) {
        String name = input.trim();
        if (name.isEmpty()) return "";
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) name = name.substring(0, name.length() - 4).trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Enter a name before .mp3.");
        }
        boolean invalid = name.startsWith(".") || name.endsWith(".");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            invalid |= Character.isISOControl(c) || "\\/:*?\"<>|".indexOf(c) >= 0;
        }
        if (invalid) {
            throw new IllegalArgumentException("Use a filename without slashes, control characters, or : * ? \" < > |.");
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > 200) {
            throw new IllegalArgumentException("This filename is too long. Please shorten it.");
        }
        return name + ".mp3";
    }
}
