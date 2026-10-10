package com.andre.yt2mp3;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ConversionFilesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void onlyFinishedMp3IsPublished() throws Exception {
        File dir = temp.newFolder();
        Files.write(new File(dir, "audio.webm.part").toPath(), new byte[]{1});
        File mp3 = new File(dir, "song.mp3");
        Files.write(mp3.toPath(), new byte[]{1, 2, 3});
        assertEquals(mp3, ConversionFiles.completedMp3(dir));
    }

    @Test(expected = java.io.IOException.class) public void emptyMp3CannotReportSuccess() throws Exception {
        File dir = temp.newFolder();
        assertTrue(new File(dir, "song.mp3").createNewFile());
        ConversionFiles.completedMp3(dir);
    }

    @Test(expected = java.io.IOException.class) public void intermediateAudioCannotReportSuccess() throws Exception {
        File dir = temp.newFolder();
        Files.write(new File(dir, "audio.webm").toPath(), new byte[]{1});
        ConversionFiles.completedMp3(dir);
    }

    @Test public void failedJobCleanupRemovesPartialFiles() throws Exception {
        File dir = temp.newFolder();
        File nested = new File(dir, "temp");
        assertTrue(nested.mkdir());
        Files.write(new File(nested, "audio.part").toPath(), new byte[]{1});
        ConversionFiles.cleanUp(dir);
        assertFalse(dir.exists());
    }
}
