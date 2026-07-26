package com.andre.yt2mp3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class OutputTemplateTest {
    @Test
    public void usesRequestedFolderName() {
        File downloads = new File("/storage/emulated/0/Download");
        assertEquals("yt2mp3", OutputTemplate.outputDirectory(downloads).getName());
    }

    @Test
    public void mp3TemplateIncludesTitleIdAndExtension() {
        String template = OutputTemplate.mp3Template(new File("/storage/emulated/0/Download/yt2mp3"));
        assertTrue(template.contains("%(title).120B"));
        assertTrue(template.contains("%(id)s"));
        assertTrue(template.endsWith("%(ext)s"));
    }
}
