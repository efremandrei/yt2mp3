package com.andre.yt2mp3;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class YoutubeUrlTest {
    @Test
    public void acceptsYoutubeWatchUrls() {
        assertTrue(YoutubeUrl.isSupported("https://www.youtube.com/watch?v=abc123"));
        assertTrue(YoutubeUrl.isSupported("https://m.youtube.com/shorts/abc123"));
        assertTrue(YoutubeUrl.isSupported("https://music.youtube.com/watch?v=abc123"));
    }

    @Test
    public void acceptsShortUrls() {
        assertTrue(YoutubeUrl.isSupported("https://youtu.be/abc123"));
    }

    @Test
    public void rejectsLookalikesAndUnsupportedSchemes() {
        assertFalse(YoutubeUrl.isSupported("https://notyoutube.com/watch?v=abc123"));
        assertFalse(YoutubeUrl.isSupported("https://youtube.com.evil.example/watch?v=abc123"));
        assertFalse(YoutubeUrl.isSupported("ftp://youtube.com/watch?v=abc123"));
        assertFalse(YoutubeUrl.isSupported(""));
    }
}
