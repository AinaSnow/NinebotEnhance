package dev.ichinomiya.ninebotenhance.core;

/** PlaybackState positions use elapsed realtime; never advance paused/unknown positions. */
public final class MusicPlayback {
    /** Paused/loading/seeking sessions retain a track; none/stopped/error sessions do not. */
    public static boolean hasTrack(int state){return state>=2&&state<=6||state>=8&&state<=11;}
    public static boolean moving(int state){return state==3||state==4||state==5;}
    public static long position(long position,long duration,int state,float speed,long updated,long now){
        if(position<0)return -1;
        double value=position;
        if(moving(state)&&Float.isFinite(speed)&&updated>0&&now>updated)value+=(now-updated)*(double)speed;
        long result=(long)Math.max(0,Math.min(Long.MAX_VALUE,value));
        return duration>0?Math.min(duration,result):result;
    }
    public static String time(long millis){if(millis<0)return "--:--";long seconds=millis/1000;return (seconds/60)+":"+(seconds%60<10?"0":"")+(seconds%60);}
    private MusicPlayback(){}
}
