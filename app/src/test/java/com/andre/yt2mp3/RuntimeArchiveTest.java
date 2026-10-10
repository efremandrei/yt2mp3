package com.andre.yt2mp3;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class RuntimeArchiveTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File archive(String path) throws Exception {
        File archive = temp.newFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(archive))) {
            out.putNextEntry(new ZipEntry(path));
            out.write(new byte[]{0x7f, 'E', 'L', 'F', 1});
            out.closeEntry();
        }
        return archive;
    }

    @Test public void repairsMissingLibraryInExistingCache() throws Exception {
        File zip = archive("usr/lib/libpython3.12.so.1.0");
        File runtime = temp.newFolder();
        File library = new File(runtime, "usr/lib/libpython3.12.so.1.0");
        assertTrue(RuntimeArchive.ensureExtracted(zip, runtime));
        assertTrue(library.delete());
        File userData = new File(runtime.getParentFile(), "saved-settings");
        Files.write(userData.toPath(), new byte[]{42});
        assertTrue(RuntimeArchive.ensureExtracted(zip, runtime));
        assertEquals(5, library.length());
        assertArrayEquals(new byte[]{42}, Files.readAllBytes(userData.toPath()));
    }

    @Test public void repairsTruncatedLibraryAndKeepsCompleteCache() throws Exception {
        File zip = archive("usr/lib/libpython3.12.so.1.0");
        File runtime = temp.newFolder();
        RuntimeArchive.ensureExtracted(zip, runtime);
        File library = new File(runtime, "usr/lib/libpython3.12.so.1.0");
        Files.write(library.toPath(), new byte[]{1});
        assertTrue(RuntimeArchive.ensureExtracted(zip, runtime));
        assertFalse(RuntimeArchive.ensureExtracted(zip, runtime));
        assertEquals(5, library.length());
    }

    @Test(expected = java.io.IOException.class) public void rejectsArchiveTraversal() throws Exception {
        RuntimeArchive.ensureExtracted(archive("../outside"), temp.newFolder());
    }

    @Test public void symlinkPayloadIsNeverWrittenAsLibraryBytes() throws Exception {
        File zip = temp.newFile();
        try (org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream out =
                     new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(zip)) {
            org.apache.commons.compress.archivers.zip.ZipArchiveEntry link =
                    new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("libz.so.1");
            link.setUnixMode(0120777);
            out.putArchiveEntry(link);
            out.write("libz.so.1.3.1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeArchiveEntry();
        }
        File runtime = temp.newFolder();
        File existing = new File(runtime, "libz.so.1");
        Files.write(existing.toPath(), new byte[]{0x7f, 'E', 'L', 'F'});
        assertFalse(RuntimeArchive.ensureExtracted(zip, runtime));
        assertArrayEquals(new byte[]{0x7f, 'E', 'L', 'F'}, Files.readAllBytes(existing.toPath()));
    }
}
