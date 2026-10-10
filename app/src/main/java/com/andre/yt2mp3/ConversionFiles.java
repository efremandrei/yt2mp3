package com.andre.yt2mp3;

import java.io.File;
import java.io.IOException;

/** A conversion succeeds only when the job produced a nonempty MP3. */
final class ConversionFiles {
    private ConversionFiles() { }

    static File completedMp3(File directory) throws IOException {
        File[] files = directory.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(java.util.Locale.US).endsWith(".mp3")
                && file.length() > 0);
        if (files == null || files.length != 1) {
            throw new IOException("Converter did not produce one complete MP3 file.");
        }
        return files[0];
    }

    static void cleanUp(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) cleanUp(file);
                else file.delete();
            }
        }
        directory.delete();
    }
}
