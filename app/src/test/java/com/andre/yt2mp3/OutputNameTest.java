package com.andre.yt2mp3;

import org.junit.Test;
import static org.junit.Assert.*;

public class OutputNameTest {
    @Test public void blankUsesAutomaticVideoTitle() { assertEquals("", OutputName.custom("  ")); }
    @Test public void preservesSpacesUnicodeAndLiteralTemplateCharacters() {
        assertEquals("My song מצפן 100%.mp3", OutputName.custom(" My song מצפן 100% "));
        assertEquals("%(title)s.mp3", OutputName.custom("%(title)s"));
    }
    @Test public void addsExactlyOneCaseNormalizedExtension() {
        assertEquals("My song.mp3", OutputName.custom("My song.MP3"));
        assertEquals("My song.mp3", OutputName.custom("My song"));
    }
    @Test public void rejectsUnsafeOrEmptyNames() {
        for (String name : new String[]{"../song", "dir\\song", ".mp3", "..", "a:b", "a\nb", "a\u0000b"}) {
            try { OutputName.custom(name); fail(name); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void limitsUtf8Bytes() {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 101; i++) name.append('ש');
        try { OutputName.custom(name.toString()); fail(); } catch (IllegalArgumentException expected) { }
    }
}
