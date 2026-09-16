package dev.ichinomiya.ninebotenhance.notification;

import android.content.*;
import android.media.AudioManager;
import android.os.*;

/** Reports the phone volume level of the stream that last changed. Levels only; no audio content or session identity. */
public final class VolumeStatus {
    private static final String CHANGED="android.media.VOLUME_CHANGED_ACTION",STREAM="android.media.EXTRA_VOLUME_STREAM_TYPE",
            VALUE="android.media.EXTRA_VOLUME_STREAM_VALUE",PREVIOUS="android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";
    private final AudioManager audio;private int stream=AudioManager.STREAM_MUSIC,level=-1,max=15,polledMusic=-1;private long seq,changedAt;
    public VolumeStatus(Context context){
        AudioManager manager=null;
        try{manager=context.getSystemService(AudioManager.class);}catch(RuntimeException ignored){}
        audio=manager;
        if(audio!=null)try{level=polledMusic=audio.getStreamVolume(AudioManager.STREAM_MUSIC);max=Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));}catch(RuntimeException ignored){}
        // The framework-protected broadcast carries the stream and both values; other apps cannot send it.
        try{context.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
            if(intent==null||!CHANGED.equals(intent.getAction()))return;
            int type=intent.getIntExtra(STREAM,-1),value=intent.getIntExtra(VALUE,-1),previous=intent.getIntExtra(PREVIOUS,-1);
            if(type<0||value<0||value==previous)return;record(type,value);
        }},new IntentFilter(CHANGED),Context.RECEIVER_EXPORTED);}catch(RuntimeException ignored){}
    }
    private synchronized void record(int type,int value){
        if(audio==null)return;
        int limit;try{limit=Math.max(1,audio.getStreamMaxVolume(type));}catch(RuntimeException e){limit=max;}
        stream=type;level=Math.max(0,Math.min(limit,value));max=limit;seq++;changedAt=SystemClock.elapsedRealtime();
        if(type==AudioManager.STREAM_MUSIC)polledMusic=level;
    }
    /** Polling the media stream as well covers devices that deliver the broadcast late or not at all. */
    public synchronized Bundle snapshot(){
        if(audio!=null)try{int value=audio.getStreamVolume(AudioManager.STREAM_MUSIC);if(polledMusic>=0&&value!=polledMusic)record(AudioManager.STREAM_MUSIC,value);polledMusic=value;}catch(RuntimeException ignored){}
        Bundle b=new Bundle();b.putInt("stream",stream);b.putInt("level",level);b.putInt("max",max);b.putLong("seq",seq);b.putLong("changed",changedAt);return b;
    }
}
