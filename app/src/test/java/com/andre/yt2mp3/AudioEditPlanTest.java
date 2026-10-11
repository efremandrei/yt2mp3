package com.andre.yt2mp3;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;

public class AudioEditPlanTest {
    @Test public void parsesSecondsMinutesHoursAndFractions() {
        assertEquals(1250, AudioEditPlan.parseTime("1.250"));
        assertEquals(84500, AudioEditPlan.parseTime("1:24.500"));
        assertEquals(3723004, AudioEditPlan.parseTime("1:02:03.004"));
        assertEquals(3600000, AudioEditPlan.parseTime("60:00"));
    }
    @Test public void rejectsInvalidTimesAndRanges() {
        for (String time : new String[]{"-1", "NaN", "1:60", "1::2", "1.5:20", "", "Infinity"}) {
            try { AudioEditPlan.parseTime(time); fail(time); } catch (IllegalArgumentException expected) { }
        }
        for (long[] range : new long[][]{{-1,100}, {100,100}, {200,100}, {0,1001}}) {
            try { new AudioChunk("bad",range[0],range[1],1000); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void concatenatesInUserOrderWithoutSorting() {
        AudioChunk later = new AudioChunk("Later", 2000,2800,3000), earlier = new AudioChunk("Earlier",0,800,3000);
        String graph = AudioEditPlan.filter(Arrays.asList(later,earlier));
        assertTrue(graph.indexOf("start=2.000") < graph.indexOf("start=0.000"));
        assertTrue(graph.contains("asetpts=PTS-STARTPTS"));
        assertTrue(graph.endsWith("[c0][c1]concat=n=2:v=0:a=1[out]"));
        assertEquals(1600, AudioEditPlan.duration(Arrays.asList(later,earlier)));
    }
    @Test public void supportsSingleClipAndRejectsEmptyPlan() {
        assertTrue(AudioEditPlan.filter(Collections.singletonList(new AudioChunk("Clip",0,1000,1000))).contains("concat=n=1"));
        try { AudioEditPlan.filter(Collections.emptyList()); fail(); } catch (IllegalArgumentException expected) { }
    }
}
