package com.andre.yt2mp3;

final class AudioChunk {
    final String name;
    final long fromMs, toMs;
    boolean selected = true;
    AudioChunk(String name, long fromMs, long toMs, long durationMs) {
        if (fromMs < 0 || toMs <= fromMs || toMs > durationMs) {
            throw new IllegalArgumentException("Use a start before the end, within the file duration.");
        }
        this.name = name; this.fromMs = fromMs; this.toMs = toMs;
    }
}
