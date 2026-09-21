package dev.ichinomiya.ninebotenhance.core;

import java.util.Locale;

/**
 * Binding of one TX lamp hoist: its BLE address, the six digit password the device authenticates with, the travel speed and how
 * many notches the usable range is split into, plus whether the light direction is flipped. {@code speed} is the small
 * controller's 1–20 step scale; the protocol carries it times five.
 * <p>
 * The hoist reports its own travel limits, and the range between them is split into {@code steps} equal notches: one volume press
 * moves one notch and the top of travel reads as 100 %, whatever raw height that turns out to be. {@code reversed} swaps which
 * end is bright — done entirely in the module by mirroring the target, so the device is never sent its own direction bit.
 */
public record LampSettings(String mac,String password,int speed,int steps,boolean reversed,boolean volumeControl){
    public static final int MIN_SPEED=1,MAX_SPEED=20,DEFAULT_SPEED=10;
    public static final int MIN_STEPS=5,MAX_STEPS=15,DEFAULT_STEPS=8;
    /** The hoist never targets the reported upper limit itself: one raw unit below it is the usable top (0–73 reaches 72). */
    public static final int TOP_MARGIN=1;
    public static int topLimit(int low,int high){int lo=Math.min(low,high),hi=Math.max(low,high);return hi-lo>=2?hi-TOP_MARGIN:hi;}
    public static final LampSettings NONE=new LampSettings("","",DEFAULT_SPEED,DEFAULT_STEPS,false,true);
    public LampSettings{
        mac=normalizeMac(mac);
        password=password==null?"":password.trim();
        speed=Math.max(MIN_SPEED,Math.min(MAX_SPEED,speed));
        steps=Math.max(MIN_STEPS,Math.min(MAX_STEPS,steps));
    }
    /** Both halves of the binding are needed: the device ignores every command until the password is accepted. */
    public boolean bound(){return !mac.isEmpty()&&TxLampProtocol.validPassword(password);}
    /** Protocol speed 0–100 from the 1–20 step scale, like the vendor application's slider. */
    public int protocolSpeed(){return Math.max(TxLampProtocol.MIN_SPEED,Math.min(TxLampProtocol.MAX_SPEED,speed*5));}
    /** Raw device units moved by one volume press: the reported travel range split into the step count, at least one unit. */
    public int stepUnits(int low,int high){return Math.max(1,Math.round(Math.max(0,topLimit(low,high)-Math.min(low,high))/(float)steps));}
    /** Displayed brightness 0–100 for a raw position between the reported travel limits, flipped when reversed; -1 when unknown. */
    public int displayPercent(int position,int low,int high){
        if(position<0)return -1;
        int lo=Math.min(low,high),hi=topLimit(low,high);
        if(hi<=lo)return reversed?100:0;
        int normal=Math.max(0,Math.min(100,Math.round((Math.max(lo,Math.min(hi,position))-lo)*100f/(hi-lo))));
        return reversed?100-normal:normal;
    }
    /** Displayed size of one notch, e.g. 20 for five notches; UI only. */
    public int stepPercent(){return Math.max(1,Math.round(100f/steps));}
    public LampSettings withMac(String value){return new LampSettings(value,password,speed,steps,reversed,volumeControl);}
    public LampSettings withPassword(String value){return new LampSettings(mac,value,speed,steps,reversed,volumeControl);}
    public String label(){return bound()?mac+" · "+steps+" 档":"未绑定";}
    /** Upper case colon form, or an empty string when the text is not a BLE address. */
    public static String normalizeMac(String value){
        if(value==null)return "";
        String text=value.trim().toUpperCase(Locale.ROOT).replace('-',':');
        return validMac(text)?text:"";
    }
    public static boolean validMac(String value){
        if(value==null||value.length()!=17)return false;
        for(int i=0;i<17;i++){
            char c=value.charAt(i);
            if(i%3==2){if(c!=':')return false;continue;}
            if((c<'0'||c>'9')&&(c<'A'||c>'F'))return false;
        }
        return true;
    }
    /** Advertising name of a bound device: MOTORE plus the last three address bytes, as the vendor firmware builds it. */
    public static String advertisedName(String mac){
        String normalized=normalizeMac(mac);
        return normalized.isEmpty()?"":TxLampProtocol.NAME_PREFIX+normalized.substring(9).replace(":","");
    }
}
