package com.andre.yt2mp3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class RollingLogTest {
    @Test
    public void logFileUsesOutputDirectory() {
        File outputDirectory = new File("build/test-output/yt2mp3");

        File logFile = RollingLog.logFile(outputDirectory);

        assertEquals(outputDirectory, logFile.getParentFile());
        assertEquals("yt2mp3.log", logFile.getName());
    }

    @Test
    public void appendKeepsOnlyNewestThousandLines() throws Exception {
        File outputDirectory = Files.createTempDirectory("yt2mp3-log").toFile();

        for (int index = 1; index <= 1005; index++) {
            RollingLog.append(outputDirectory, String.format("line %04d", index));
        }

        List<String> lines = Files.readAllLines(
                RollingLog.logFile(outputDirectory).toPath(), StandardCharsets.UTF_8);
        assertEquals(1000, lines.size());
        assertTrue(lines.get(0).endsWith("line 0006"));
        assertTrue(lines.get(999).endsWith("line 1005"));
    }
}
