package com.andre.yt2mp3;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Publish only the finished MP3; intermediates remain in the app's cache. */
final class Mp3Publisher {
    private Mp3Publisher() { }

    static String publish(Context context, File source, String customName) throws IOException {
        String name = customName.isEmpty() ? source.getName() : OutputName.custom(customName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return publishScoped(context.getContentResolver(), source, name);
        }
        File directory = OutputTemplate.outputDirectory(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create output folder. Allow storage access and retry.");
        }
        File output = new File(directory, name);
        for (int i = 1; output.exists(); i++) {
            output = new File(directory, name.substring(0, name.length() - 4) + " (" + i + ").mp3");
        }
        try (OutputStream stream = new FileOutputStream(output)) {
            copy(source, stream);
        } catch (IOException error) {
            output.delete();
            throw error;
        }
        MediaScannerConnection.scanFile(context, new String[]{output.getAbsolutePath()},
                new String[]{"audio/mpeg"}, null);
        return "Download/yt2mp3/" + output.getName();
    }

    @android.annotation.TargetApi(29)
    private static String publishScoped(ContentResolver resolver, File source, String name) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/yt2mp3/");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Could not create MP3 in Downloads.");
        boolean published = false;
        try {
            try (OutputStream stream = resolver.openOutputStream(uri, "w")) {
                if (stream == null) throw new IOException("Could not open MP3 output.");
                copy(source, stream);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) {
                throw new IOException("Could not finish saving MP3.");
            }
            published = true;
            // MediaStore can suffix duplicate names. Report the actual saved name.
            try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            return "Download/yt2mp3/" + name;
        } finally {
            if (!published) resolver.delete(uri, null, null);
        }
    }

    private static void copy(File source, OutputStream destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) destination.write(buffer, 0, count);
        }
    }
}
