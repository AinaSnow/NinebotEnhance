package dev.ichinomiya.ninebotenhance.core;

import java.nio.charset.StandardCharsets;

/**
 * Scripted navigation data for the dashboard test: a 3 km straight run at 8 m/s split into 500 m segments, each with its own
 * turn icon and next road name, ending in the "arrived" icon. Payload layouts are the ones Ninebot's DashNaviDataMessenger
 * writes to the TFT board (command 113): all integers little-endian, road names UTF-8 with a trailing zero byte.
 */
public final class NaviTestData {
    public static final int TOTAL_METERS=3000,SPEED_MPS=8,SEGMENT_METERS=500,GPS_LEVEL=1;
    public static final long TICK_MS=1000;
    public static final String CURRENT_ROAD="测试路";
    public static final String[] NEXT_ROADS={"九号大道","科技园路","创新街","环岛路","中关村东路","目的地"};
    /** NaviState values: straight, left, right, left-front, right-front, u-turn; 15 = arrived destination. */
    public static final int[] ICONS={9,2,3,4,5,8};
    public static final int ICON_ARRIVED=15;
    public static final String CMD_DISTANCE="setNaviDistance",CMD_INFO="setNaviInfo",CMD_ROAD="setNaviRoad",CMD_ROAD_NEXT="setNaviRoadNext",CMD_DRIVE="setNaviDriveInfo";
    public record Step(int remaining,int remainingSeconds,int icon,int stepRemaining,int lights,String nextRoad,int driven,int elapsedSeconds){
        public boolean arrived(){return remaining==0;}
    }
    /** State of the scripted route after the given time. */
    public static Step at(long elapsedMs){
        long elapsedSeconds=Math.max(0,elapsedMs/1000);
        int driven=(int)Math.min(TOTAL_METERS,elapsedSeconds*SPEED_MPS);
        int remaining=TOTAL_METERS-driven;
        int segment=Math.min(driven/SEGMENT_METERS,NEXT_ROADS.length-1);
        int stepRemaining=remaining==0?0:SEGMENT_METERS-driven%SEGMENT_METERS;
        int icon=remaining==0?ICON_ARRIVED:ICONS[segment%ICONS.length];
        int lights=Math.max(0,3-segment/2);
        return new Step(remaining,remaining/SPEED_MPS,icon,stepRemaining,lights,NEXT_ROADS[segment],driven,(int)Math.min(elapsedSeconds,Integer.MAX_VALUE));
    }
    public static byte[] distance(int meters){byte[] b=new byte[4];putInt(b,0,meters);return b;}
    /** 18 bytes: remaining m, remaining s, icon, current step remaining m, remaining traffic lights, GPS level. */
    public static byte[] info(int remaining,int seconds,int icon,int stepRemaining,int lights,int gps){
        byte[] b=new byte[18];putInt(b,0,remaining);putInt(b,4,seconds);putShort(b,8,icon);putInt(b,10,stepRemaining);putShort(b,14,lights);putShort(b,16,gps);return b;
    }
    public static byte[] info(Step step){return info(step.remaining(),step.remainingSeconds(),step.icon(),step.stepRemaining(),step.lights(),GPS_LEVEL);}
    public static byte[] driveInfo(int driven,int seconds){byte[] b=new byte[8];putInt(b,0,driven);putInt(b,4,seconds);return b;}
    public static byte[] driveInfo(Step step){return driveInfo(step.driven(),step.elapsedSeconds());}
    public static byte[] text(String value){
        byte[] utf8=value.getBytes(StandardCharsets.UTF_8);byte[] b=new byte[utf8.length+1];System.arraycopy(utf8,0,b,0,utf8.length);return b;
    }
    static void putInt(byte[] b,int at,int value){b[at]=(byte)value;b[at+1]=(byte)(value>>8);b[at+2]=(byte)(value>>16);b[at+3]=(byte)(value>>24);}
    static void putShort(byte[] b,int at,int value){b[at]=(byte)value;b[at+1]=(byte)(value>>8);}
    private NaviTestData(){}
}
