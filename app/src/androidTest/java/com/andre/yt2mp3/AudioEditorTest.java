package com.andre.yt2mp3;

import android.content.Context;
import android.content.Intent;
import android.app.Instrumentation;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.CheckBox;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;

@RunWith(AndroidJUnit4.class)
public class AudioEditorTest {
    static void writeToneWave(File file) throws Exception {
        int rate = 48000, samples = rate * 3, size = samples * 2;
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeBytes("RIFF"); le32(out,36+size); out.writeBytes("WAVEfmt "); le32(out,16);
            le16(out,1); le16(out,1); le32(out,rate); le32(out,rate*2); le16(out,2); le16(out,16);
            out.writeBytes("data"); le32(out,size);
            int[] frequencies={220,440,880};
            for(int i=0;i<samples;i++) le16(out,(int)(Math.sin(2*Math.PI*frequencies[i/rate]*i/rate)*12000));
        }
    }
    private static void le32(DataOutputStream out,int value) throws Exception { out.writeInt(Integer.reverseBytes(value)); }
    private static void le16(DataOutputStream out,int value) throws Exception { out.writeShort(Short.reverseBytes((short)value)); }
    private static byte[] bytes(File file) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        return out.toByteArray();
    }
    private static double frequency(byte[] pcm,double from,double to) {
        int start=(int)(from*8000), end=(int)(to*8000), crossings=0;
        short previous=0;
        for(int i=start;i<end;i++){
            short sample=(short)((pcm[i*2]&255)|(pcm[i*2+1]<<8));
            if(i>start&&previous<=0&&sample>0)crossings++;previous=sample;
        }
        return crossings/(to-from);
    }
    @Test public void realMp3CutsAndReorderedMergeHaveCorrectAudioAndDuration() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        EngineRuntime.initialize(context);
        File directory=new File(context.getCacheDir(),"editor-audio-test-"+System.nanoTime());assertTrue(directory.mkdir());
        try {
            File wav=new File(directory,"tones.wav"),source=new File(directory,"source.mp3"),merged=new File(directory,"merged.mp3"),cut=new File(directory,"cut.mp3"),pcm=new File(directory,"merged.pcm");
            writeToneWave(wav);AudioEditEngine engine=new AudioEditEngine();
            engine.run(context,Arrays.asList("-y","-i",wav.getAbsolutePath(),"-c:a","libmp3lame","-q:a","2",source.getAbsolutePath()));
            byte[] originalHash=MessageDigest.getInstance("SHA-256").digest(bytes(source));
            engine.render(context,source,Arrays.asList(new AudioChunk("Later",2050,2850,3000),new AudioChunk("Earlier",50,850,3000)),merged);
            assertEquals(1600,EditorActivity.readDuration(merged),80);
            engine.run(context,Arrays.asList("-y","-i",merged.getAbsolutePath(),"-f","s16le","-ac","1","-ar","8000",pcm.getAbsolutePath()));
            byte[] decoded=bytes(pcm);
            assertEquals(880,frequency(decoded,.15,.5),15);assertEquals(220,frequency(decoded,1.0,1.3),15);
            engine.render(context,source,Collections.singletonList(new AudioChunk("Middle",1100,1600,3000)),cut);
            assertEquals(500,EditorActivity.readDuration(cut),80);
            assertArrayEquals(originalHash,MessageDigest.getInstance("SHA-256").digest(bytes(source)));
            engine.cancel();
            try{engine.render(context,source,Collections.singletonList(new AudioChunk("Stop",0,1000,3000)),new File(directory,"canceled.mp3"));fail();}catch(InterruptedException expected){}
        }finally{ConversionFiles.cleanUp(directory);}
    }
    @Test public void userCanAddSelectReorderAndEditChunks() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        File source=File.createTempFile("editor-ui-test-",".wav",context.getCacheDir());writeToneWave(source);
        EditorActivity activity=(EditorActivity)instrumentation.startActivitySync(new Intent(context,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            instrumentation.runOnMainSync(()->{
                activity.setSource(source,"Test song.wav",3000);
                add(activity,"First","0.1","0.8");add(activity,"Second","2.0","2.9");
                assertEquals(2,activity.selectedChunks().size());
                findDescription(activity.getWindow().getDecorView(),"Move Second up").performClick();
                assertEquals("Second",activity.selectedChunks().get(0).name);
                CheckBox first=findCheck(activity.getWindow().getDecorView(),"First");first.setChecked(false);
                assertEquals(1,activity.selectedChunks().size());assertEquals("Second",activity.selectedChunks().get(0).name);
                findButton(activity.getWindow().getDecorView(),"Edit").performClick();
                ((EditText)activity.findViewById(R.id.editor_from)).setText("1.2");
                ((EditText)activity.findViewById(R.id.editor_to)).setText("1.8");
                activity.findViewById(R.id.editor_add).performClick();
                assertEquals(1200,activity.selectedChunks().get(0).fromMs);
                assertEquals(1800,activity.selectedChunks().get(0).toMs);
                findDescription(activity.getWindow().getDecorView(),"Remove Second").performClick();
                assertTrue(activity.selectedChunks().isEmpty());
            });
        }finally{instrumentation.runOnMainSync(activity::finish);source.delete();}
    }
    private static void add(EditorActivity activity,String name,String from,String to){
        ((EditText)activity.findViewById(R.id.editor_chunk_name)).setText(name);((EditText)activity.findViewById(R.id.editor_from)).setText(from);((EditText)activity.findViewById(R.id.editor_to)).setText(to);activity.findViewById(R.id.editor_add).performClick();
    }
    private static View findDescription(View view,String description){
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}
        return null;
    }
    private static android.widget.Button findButton(View view,String name){
        if(view instanceof android.widget.Button&&name.contentEquals(((android.widget.Button)view).getText()))return(android.widget.Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){android.widget.Button found=findButton(((ViewGroup)view).getChildAt(i),name);if(found!=null)return found;}
        return null;
    }
    private static CheckBox findCheck(View view,String name){
        if(view instanceof CheckBox&&name.contentEquals(((CheckBox)view).getText()))return(CheckBox)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){CheckBox found=findCheck(((ViewGroup)view).getChildAt(i),name);if(found!=null)return found;}
        return null;
    }
}
