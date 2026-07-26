package com.andre.yt2mp3;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class YoutubeUrl {
    private YoutubeUrl() {
    }

    static boolean isSupported(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.US);
            if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.US);
            return normalizedHost.equals("youtu.be")
                    || normalizedHost.equals("youtube.com")
                    || normalizedHost.endsWith(".youtube.com");
        } catch (URISyntaxException error) {
            return false;
        }
    }
}
