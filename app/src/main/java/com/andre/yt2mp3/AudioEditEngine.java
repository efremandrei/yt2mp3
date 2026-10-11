package com.andre.yt2mp3;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class AudioEditEngine {
    private volatile Process process;
    private final AtomicBoolean canceled = new AtomicBoolean();
    void reset() { canceled.set(false); }
    void cancel() { canceled.set(true); Process current = process; if (current != null) current.destroy(); }
    boolean isCanceled() { return canceled.get(); }

    void render(Context context, File source, List<AudioChunk> chunks, File output) throws Exception {
        if (chunks.isEmpty()) throw new IllegalArgumentException("Select at least one chunk.");
        run(context, Arrays.asList("-y", "-i", source.getAbsolutePath(), "-filter_complex", AudioEditPlan.filter(chunks),
                "-map", "[out]", "-map_metadata", "-1", "-c:a", "libmp3lame", "-q:a", "2", output.getAbsolutePath()));
        if (!output.isFile() || output.length() == 0) throw new IOException("No completed MP3 was produced.");
    }

    void run(Context context, List<String> arguments) throws Exception {
        if (canceled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Export canceled.");
        List<String> command = new ArrayList<>();
        File binaries = new File(context.getApplicationInfo().nativeLibraryDir);
        command.add(new File(binaries, "libffmpeg.so").getAbsolutePath());
        command.addAll(Arrays.asList("-hide_banner", "-nostdin", "-loglevel", "error"));
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        File packages = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages");
        builder.environment().put("LD_LIBRARY_PATH", new File(packages, "ffmpeg/usr/lib").getAbsolutePath()
                + ":" + new File(packages, "python/usr/lib").getAbsolutePath() + ":" + binaries.getAbsolutePath());
        builder.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        StringBuilder errors = new StringBuilder();
        try {
            process = builder.start();
            if (canceled.get()) process.destroy();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errors.append(line).append('\n');
                    if (errors.length() > 8000) errors.delete(0, errors.length() - 8000);
                }
            }
            int code = process.waitFor();
            if (canceled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedException("Export canceled.");
            if (code != 0) throw new IOException(errors.length() == 0 ? "Audio export failed (" + code + ")." : errors.toString().trim());
        } finally {
            Process current = process; process = null; if (current != null) current.destroy();
        }
    }
}
