package com.andre.yt2mp3;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

final class AudioEditPlan {
    private AudioEditPlan() { }
    static long parseTime(String text) {
        try {
            String[] parts = text.trim().split(":", -1);
            if (parts.length < 1 || parts.length > 3) throw new IllegalArgumentException();
            BigDecimal seconds = BigDecimal.ZERO;
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].matches(i == parts.length - 1 ? "[0-9]+(?:\\.[0-9]+)?" : "[0-9]+")) throw new IllegalArgumentException();
                BigDecimal value = new BigDecimal(parts[i]);
                if (i > 0 && value.compareTo(new BigDecimal(60)) >= 0) throw new IllegalArgumentException();
                seconds = seconds.multiply(new BigDecimal(60)).add(value);
            }
            return seconds.multiply(new BigDecimal(1000)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Use seconds, mm:ss, or hh:mm:ss (for example 1:24.500).");
        }
    }
    static String time(long ms) {
        return String.format(Locale.US, "%d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
    }
    static long duration(List<AudioChunk> chunks) {
        long result = 0;
        for (AudioChunk chunk : chunks) result = Math.addExact(result, chunk.toMs - chunk.fromMs);
        return result;
    }
    static String filter(List<AudioChunk> chunks) {
        if (chunks.isEmpty()) throw new IllegalArgumentException("Select at least one chunk.");
        StringBuilder graph = new StringBuilder();
        if (chunks.size() > 1) {
            graph.append("[0:a:0]asplit=").append(chunks.size());
            for (int i = 0; i < chunks.size(); i++) graph.append("[s").append(i).append(']');
            graph.append(';');
        }
        for (int i = 0; i < chunks.size(); i++) {
            AudioChunk chunk = chunks.get(i);
            graph.append(chunks.size() == 1 ? "[0:a:0]" : "[s" + i + "]");
            graph.append(String.format(Locale.US, "atrim=start=%.3f:end=%.3f,asetpts=PTS-STARTPTS[c%d];",
                    chunk.fromMs / 1000.0, chunk.toMs / 1000.0, i));
        }
        for (int i = 0; i < chunks.size(); i++) graph.append("[c").append(i).append(']');
        return graph.append("concat=n=").append(chunks.size()).append(":v=0:a=1[out]").toString();
    }
}
