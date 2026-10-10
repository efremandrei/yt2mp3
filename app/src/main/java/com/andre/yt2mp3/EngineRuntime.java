package com.andre.yt2mp3;

import android.content.Context;
import android.system.Os;
import android.system.ErrnoException;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.util.Collections;
import java.util.Enumeration;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

final class EngineRuntime {
    private EngineRuntime() { }

    static String initialize(Context context) throws Exception {
        File packages = new File(context.getNoBackupFilesDir(), "youtubedl-android/packages");
        File binaries = new File(context.getApplicationInfo().nativeLibraryDir);
        RuntimeArchive.ensureExtracted(new File(binaries, "libpython.zip.so"), new File(packages, "python"));
        RuntimeArchive.ensureExtracted(new File(binaries, "libffmpeg.zip.so"), new File(packages, "ffmpeg"));
        YoutubeDL.getInstance().init(context);
        FFmpeg.getInstance().init(context);
        repairLinks(new File(binaries, "libpython.zip.so"), new File(packages, "python"));
        repairLinks(new File(binaries, "libffmpeg.zip.so"), new File(packages, "ffmpeg"));
        // init() only extracts files. Execute the actual runtime before showing Ready.
        YoutubeDLRequest probe = new YoutubeDLRequest(Collections.emptyList());
        probe.addOption("--version");
        return YoutubeDL.getInstance().execute(probe, null, false, null).getOut().trim();
    }

    private static void repairLinks(File archive, File destination) throws Exception {
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (!entry.isUnixSymlink()) continue;
                File link = new File(destination, entry.getName());
                String target = zip.getUnixSymlink(entry);
                String root = destination.getCanonicalPath() + File.separator;
                if (!link.getParentFile().getCanonicalPath().startsWith(root)
                        || !new File(link.getParentFile(), target).getCanonicalPath().startsWith(root)) {
                    throw new java.io.IOException("Runtime link escapes its directory");
                }
                try {
                    if (target.equals(Os.readlink(link.getAbsolutePath()))) continue;
                } catch (ErrnoException notALink) {
                    // Missing or incorrectly extracted link; restore the archive's link.
                }
                if (link.exists() && !link.delete()) throw new java.io.IOException("Cannot repair " + link);
                // delete also handles dangling symbolic links, whose exists() is false.
                link.delete();
                Os.symlink(target, link.getAbsolutePath());
            }
        }
    }
}
