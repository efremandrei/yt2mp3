package com.andre.yt2mp3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/** Repair disposable native runtime files before the downloader checks its size-only cache. */
final class RuntimeArchive {
    private RuntimeArchive() { }

    static boolean ensureExtracted(File archive, File destination) throws IOException {
        boolean repaired = false;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                File target = new File(destination, entry.getName());
                if (!target.getCanonicalPath().startsWith(destination.getCanonicalPath() + File.separator)) {
                    throw new IOException("Runtime archive entry escapes its directory: " + entry.getName());
                }
                // A ZIP symlink's size is its target name, not the library's size.
                // Android recreates these links separately after regular files.
                if (entry.isDirectory() || entry.isUnixSymlink()) continue;
                if (target.isFile() && target.length() == entry.getSize()) continue;
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Could not create runtime directory: " + parent);
                }
                File temporary = File.createTempFile("runtime-", ".tmp", parent);
                try {
                    try (InputStream input = zip.getInputStream(entry);
                         FileOutputStream output = new FileOutputStream(temporary)) {
                        byte[] buffer = new byte[32768];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    if (temporary.length() != entry.getSize()) {
                        throw new IOException("Incomplete runtime file: " + entry.getName());
                    }
                    if (target.exists() && !target.delete()) {
                        throw new IOException("Could not remove incomplete runtime file: " + entry.getName());
                    }
                    if (!temporary.renameTo(target)) {
                        throw new IOException("Could not replace runtime file: " + entry.getName());
                    }
                    repaired = true;
                } finally {
                    temporary.delete();
                }
            }
        }
        return repaired;
    }
}
