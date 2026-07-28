package com.andre.yt2mp3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

final class RollingLog {
    static final int MAX_LINES = 1000;
    static final String FILE_NAME = "yt2mp3.log";

    private RollingLog() {
    }

    static File logFile(File outputDirectory) {
        return new File(outputDirectory, FILE_NAME);
    }

    static synchronized void append(File outputDirectory, String message) throws IOException {
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("Could not create " + outputDirectory.getAbsolutePath());
        }

        File logFile = logFile(outputDirectory);
        ArrayDeque<String> lines = new ArrayDeque<>(MAX_LINES);
        if (logFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.addLast(line);
                    while (lines.size() >= MAX_LINES) {
                        lines.removeFirst();
                    }
                }
            }
        }

        lines.addLast(timestamp() + " " + sanitize(message));
        writeLines(logFile, lines);
    }

    private static void writeLines(File logFile, ArrayDeque<String> lines) throws IOException {
        File tempFile = new File(logFile.getParentFile(), logFile.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempFile, false), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        if (logFile.exists() && !logFile.delete()) {
            throw new IOException("Could not replace " + logFile.getAbsolutePath());
        }
        if (!tempFile.renameTo(logFile)) {
            throw new IOException("Could not write " + logFile.getAbsolutePath());
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String sanitize(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "(empty)";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
