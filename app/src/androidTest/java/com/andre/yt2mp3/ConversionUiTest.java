package com.andre.yt2mp3;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.MediaStore;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConversionUiTest {
    @Test public void clearEmptiesUrlAndKeepsChosenFilename() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                EditText url = activity.findViewById(R.id.url_input);
                EditText filename = activity.findViewById(R.id.filename_input);
                url.setText("https://youtu.be/test");
                filename.setText("My song");
                activity.findViewById(R.id.clear_button).performClick();
                assertEquals("", url.getText().toString());
                assertEquals("My song", filename.getText().toString());
                android.widget.TextView preview = activity.findViewById(R.id.output_label);
                assertTrue(preview.getText().toString().contains("My song.mp3"));
                activity.findViewById(R.id.light_skin_button).performClick();
                activity.findViewById(R.id.dark_skin_button).performClick();
            });
            // Let startup finish before the next test deliberately removes runtime files.
            String[] status = {""};
            long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
            do {
                instrumentation.runOnMainSync(() -> status[0] =
                        ((android.widget.TextView) activity.findViewById(R.id.status_text)).getText().toString());
                if (status[0].startsWith("Ready.")) break;
                android.os.SystemClock.sleep(100);
            } while (android.os.SystemClock.elapsedRealtime() < deadline);
            assertTrue(status[0], status[0].startsWith("Ready."));
            instrumentation.runOnMainSync(() -> {
                assertEquals(android.view.View.GONE, activity.findViewById(R.id.splash_brand).getVisibility());
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.root_scroll).getVisibility());
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    @Test public void publishesChosenNameAndPreservesExistingFile() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "UI test מצפן " + System.currentTimeMillis() + ".mp3";
        File source = new File(context.getCacheDir(), "publish-test.mp3");
        try (FileOutputStream out = new FileOutputStream(source)) { out.write(new byte[]{'I','D','3',1,2,3}); }
        String first = "", second = "";
        try {
            first = Mp3Publisher.publish(context, source, name);
            second = Mp3Publisher.publish(context, source, name);
            assertEquals("Download/yt2mp3/" + name, first);
            assertNotEquals(first, second);
            for (String path : new String[]{first, second}) {
                try (Cursor cursor = context.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Downloads.SIZE}, MediaStore.Downloads.DISPLAY_NAME + " = ?",
                        new String[]{path.substring("Download/yt2mp3/".length())}, null)) {
                    assertNotNull(cursor); assertTrue(cursor.moveToFirst()); assertEquals(6, cursor.getLong(0));
                }
            }
        } finally {
            for (String path : new String[]{first, second}) {
                if (!path.isEmpty()) context.getContentResolver().delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.Downloads.DISPLAY_NAME + " = ?", new String[]{path.substring("Download/yt2mp3/".length())});
            }
            source.delete();
        }
    }
}
