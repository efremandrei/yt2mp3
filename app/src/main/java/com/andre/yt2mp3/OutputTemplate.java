package com.andre.yt2mp3;

import java.io.File;

final class OutputTemplate {
    static final String FOLDER_NAME = "yt2mp3";

    private OutputTemplate() {
    }

    static File outputDirectory(File downloadsDirectory) {
        return new File(downloadsDirectory, FOLDER_NAME);
    }

    static String mp3Template(File outputDirectory) {
        return new File(outputDirectory, "%(title).120B [%(id)s].%(ext)s").getAbsolutePath();
    }
}
