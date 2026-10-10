package com.andre.yt2mp3;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.content.Context;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class EngineRuntimeTest {
    @Test
    public void testMissingPythonLibraryIsRepairedAndExecutableRuns() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse(EngineRuntime.initialize(context).isEmpty());
        File library = new File(context.getNoBackupFilesDir(),
                "youtubedl-android/packages/python/usr/lib/libpython3.12.so.1.0");
        assertTrue(library.isFile());
        assertTrue(library.delete());
        YoutubeDLRequest probe = new YoutubeDLRequest(Collections.emptyList());
        probe.addOption("--version");
        try {
            YoutubeDL.getInstance().execute(probe, null, false, null);
            fail("The unrepaired cache must reproduce the reported linker failure");
        } catch (com.yausername.youtubedl_android.YoutubeDLException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("libpython3.12.so.1.0"));
        }
        assertFalse(EngineRuntime.initialize(context).isEmpty());
        assertTrue(library.length() > 1000000);
        File zlib = new File(library.getParentFile(), "libz.so.1");
        String linkTarget = android.system.Os.readlink(zlib.getAbsolutePath());
        assertTrue(zlib.delete());
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(zlib)) {
            out.write(linkTarget.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertFalse(EngineRuntime.initialize(context).isEmpty());
        assertEquals(linkTarget, android.system.Os.readlink(zlib.getAbsolutePath()));
    }
}
