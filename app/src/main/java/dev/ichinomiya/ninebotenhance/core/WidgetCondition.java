package dev.ichinomiya.ninebotenhance.core;

import java.util.Locale;

/**
 * When a widget is shown: always, for a while after a chosen change, or while every checked measurement stays inside its range.
 * A bound sitting at its slider limit is open, so the far left means no lower bound and the far right no upper bound.
 */
public record WidgetCondition(int mode,int triggers,int showSeconds,int checks,int speedMin,int speedMax,int powerMin,int powerMax,
                              int voltageMin,int voltageMax,int volumeMin,int volumeMax,int frontPressureMin,int frontPressureMax,
                              int rearPressureMin,int rearPressureMax,int frontTempMin,int frontTempMax,int rearTempMin,int rearTempMax) {
    public static final int ALWAYS=0,ON_CHANGE=1,WHILE=2;
    /** Triggers of the ON_CHANGE mode. */
    public static final int VOLUME_CHANGE=1,TRACK_CHANGE=2,PLAYBACK_CHANGE=4,ALL_TRIGGERS=7;
    /** Checks of the WHILE mode: ranges plus "music is playing". */
    public static final int SPEED=1,POWER=2,VOLTAGE=4,VOLUME=8,PLAYING=16,TYRE_FRONT_PRESSURE=32,TYRE_REAR_PRESSURE=64,TYRE_FRONT_TEMP=128,TYRE_REAR_TEMP=256,ALL_CHECKS=511;
    public static final int TYRE_CHECKS=TYRE_FRONT_PRESSURE|TYRE_REAR_PRESSURE|TYRE_FRONT_TEMP|TYRE_REAR_TEMP;
    public static final int MIN_SHOW_SECONDS=1,MAX_SHOW_SECONDS=30,DEFAULT_SHOW_SECONDS=5;
    /** Slider limits: speed km/h, power W in 10 W steps, voltage V, volume %, pressure in 0.1 bar, temperature in degrees. */
    public static final int MIN_SPEED=0,MAX_SPEED=160,MIN_POWER=0,MAX_POWER=30000,POWER_STEP=10,MIN_VOLTAGE=20,MAX_VOLTAGE=90,MIN_VOLUME=0,MAX_VOLUME=100;
    public static final int MIN_PRESSURE=12,MAX_PRESSURE=35,PRESSURE_SCALE=10,MIN_TEMP=-20,MAX_TEMP=100;
    public static final WidgetCondition ALWAYS_SHOWN=new WidgetCondition(ALWAYS,0,DEFAULT_SHOW_SECONDS,0,MIN_SPEED,MAX_SPEED,MIN_POWER,MAX_POWER,MIN_VOLTAGE,MAX_VOLTAGE,MIN_VOLUME,MAX_VOLUME);
    /** Current readings for a WHILE evaluation; NaN marks a missing or expired value. Pressures in bar, temperatures in degrees. */
    public record Measurements(float speedKmh,float power,float volts,float volumePercent,boolean playing,float frontPressure,float rearPressure,float frontTemp,float rearTemp){}
    public WidgetCondition{
        mode=clamp(mode,ALWAYS,WHILE);triggers&=ALL_TRIGGERS;checks&=ALL_CHECKS;showSeconds=clamp(showSeconds,MIN_SHOW_SECONDS,MAX_SHOW_SECONDS);
        speedMin=clamp(speedMin,MIN_SPEED,MAX_SPEED);speedMax=clamp(speedMax,MIN_SPEED,MAX_SPEED);if(speedMin>speedMax){int t=speedMin;speedMin=speedMax;speedMax=t;}
        powerMin=clamp(powerMin,MIN_POWER,MAX_POWER);powerMax=clamp(powerMax,MIN_POWER,MAX_POWER);if(powerMin>powerMax){int t=powerMin;powerMin=powerMax;powerMax=t;}
        voltageMin=clamp(voltageMin,MIN_VOLTAGE,MAX_VOLTAGE);voltageMax=clamp(voltageMax,MIN_VOLTAGE,MAX_VOLTAGE);if(voltageMin>voltageMax){int t=voltageMin;voltageMin=voltageMax;voltageMax=t;}
        volumeMin=clamp(volumeMin,MIN_VOLUME,MAX_VOLUME);volumeMax=clamp(volumeMax,MIN_VOLUME,MAX_VOLUME);if(volumeMin>volumeMax){int t=volumeMin;volumeMin=volumeMax;volumeMax=t;}
        frontPressureMin=clamp(frontPressureMin,MIN_PRESSURE,MAX_PRESSURE);frontPressureMax=clamp(frontPressureMax,MIN_PRESSURE,MAX_PRESSURE);if(frontPressureMin>frontPressureMax){int t=frontPressureMin;frontPressureMin=frontPressureMax;frontPressureMax=t;}
        rearPressureMin=clamp(rearPressureMin,MIN_PRESSURE,MAX_PRESSURE);rearPressureMax=clamp(rearPressureMax,MIN_PRESSURE,MAX_PRESSURE);if(rearPressureMin>rearPressureMax){int t=rearPressureMin;rearPressureMin=rearPressureMax;rearPressureMax=t;}
        frontTempMin=clamp(frontTempMin,MIN_TEMP,MAX_TEMP);frontTempMax=clamp(frontTempMax,MIN_TEMP,MAX_TEMP);if(frontTempMin>frontTempMax){int t=frontTempMin;frontTempMin=frontTempMax;frontTempMax=t;}
        rearTempMin=clamp(rearTempMin,MIN_TEMP,MAX_TEMP);rearTempMax=clamp(rearTempMax,MIN_TEMP,MAX_TEMP);if(rearTempMin>rearTempMax){int t=rearTempMin;rearTempMin=rearTempMax;rearTempMax=t;}
    }
    /** Older shape without tyre ranges; the tyre ranges are fully open. */
    public WidgetCondition(int mode,int triggers,int showSeconds,int checks,int speedMin,int speedMax,int powerMin,int powerMax,int voltageMin,int voltageMax,int volumeMin,int volumeMax){
        this(mode,triggers,showSeconds,checks,speedMin,speedMax,powerMin,powerMax,voltageMin,voltageMax,volumeMin,volumeMax,MIN_PRESSURE,MAX_PRESSURE,MIN_PRESSURE,MAX_PRESSURE,MIN_TEMP,MAX_TEMP,MIN_TEMP,MAX_TEMP);
    }
    public static WidgetCondition onChange(int triggers,int seconds){return new WidgetCondition(ON_CHANGE,triggers,seconds,0,MIN_SPEED,MAX_SPEED,MIN_POWER,MAX_POWER,MIN_VOLTAGE,MAX_VOLTAGE,MIN_VOLUME,MAX_VOLUME);}
    public boolean triggered(int trigger){return mode==ON_CHANGE&&(triggers&trigger)!=0;}
    public boolean uses(int check){return mode==WHILE&&(checks&check)!=0;}
    public long showMs(){return showSeconds*1000L;}
    public boolean matches(float speedKmh,float power,float volts,float volumePercent,boolean playing){return matches(new Measurements(speedKmh,power,volts,volumePercent,playing,Float.NaN,Float.NaN,Float.NaN,Float.NaN));}
    /** WHILE evaluation: every checked range needs a current value inside it and "playing" must be true when checked. */
    public boolean matches(Measurements m){
        if((checks&SPEED)!=0&&!within(m.speedKmh(),speedMin,speedMax,MIN_SPEED,MAX_SPEED,1))return false;
        // Power is compared by magnitude so a range covers driving and regeneration alike.
        if((checks&POWER)!=0&&!within(Math.abs(m.power()),powerMin,powerMax,MIN_POWER,MAX_POWER,1))return false;
        if((checks&VOLTAGE)!=0&&!within(m.volts(),voltageMin,voltageMax,MIN_VOLTAGE,MAX_VOLTAGE,1))return false;
        if((checks&VOLUME)!=0&&!within(m.volumePercent(),volumeMin,volumeMax,MIN_VOLUME,MAX_VOLUME,1))return false;
        if((checks&TYRE_FRONT_PRESSURE)!=0&&!within(m.frontPressure(),frontPressureMin,frontPressureMax,MIN_PRESSURE,MAX_PRESSURE,PRESSURE_SCALE))return false;
        if((checks&TYRE_REAR_PRESSURE)!=0&&!within(m.rearPressure(),rearPressureMin,rearPressureMax,MIN_PRESSURE,MAX_PRESSURE,PRESSURE_SCALE))return false;
        if((checks&TYRE_FRONT_TEMP)!=0&&!within(m.frontTemp(),frontTempMin,frontTempMax,MIN_TEMP,MAX_TEMP,1))return false;
        if((checks&TYRE_REAR_TEMP)!=0&&!within(m.rearTemp(),rearTempMin,rearTempMax,MIN_TEMP,MAX_TEMP,1))return false;
        return (checks&PLAYING)==0||m.playing();
    }
    /** A bound at its slider limit is open; otherwise the scaled value must be inside, and a missing value never matches. */
    private static boolean within(float value,int min,int max,int limitMin,int limitMax,int scale){
        if(Float.isNaN(value))return false;float v=value*scale;
        return (min<=limitMin||v>=min-1e-3f)&&(max>=limitMax||v<=max+1e-3f);
    }
    /** Text for a range in stored units: an open end shows as infinity, scaled values with one decimal. */
    public static String describe(int min,int max,int limitMin,int limitMax,int scale,String unit){
        boolean lower=min>limitMin,upper=max<limitMax;
        if(!lower&&!upper)return "−∞ ~ +∞";
        if(lower&&upper)return number(min,scale)+" ~ "+number(max,scale)+" "+unit;
        return lower?number(min,scale)+" "+unit+" ~ +∞":"−∞ ~ "+number(max,scale)+" "+unit;
    }
    private static String number(int value,int scale){return scale==1?String.valueOf(value):String.format(Locale.ROOT,"%.1f",value/(float)scale);}
    public String encode(){
        return mode+","+triggers+","+showSeconds+","+checks+","+speedMin+","+speedMax+","+powerMin+","+powerMax+","+voltageMin+","+voltageMax+","+volumeMin+","+volumeMax
                +","+frontPressureMin+","+frontPressureMax+","+rearPressureMin+","+rearPressureMax+","+frontTempMin+","+frontTempMax+","+rearTempMin+","+rearTempMax;
    }
    /** Twelve (older) or twenty numbers; malformed text yields the always-shown condition. */
    public static WidgetCondition parse(String text){
        if(text==null)return ALWAYS_SHOWN;String[] parts=text.split(",");if(parts.length!=12&&parts.length!=20)return ALWAYS_SHOWN;
        int[] v=new int[parts.length];try{for(int i=0;i<parts.length;i++)v[i]=Integer.parseInt(parts[i].trim());}catch(NumberFormatException e){return ALWAYS_SHOWN;}
        if(v.length==12)return new WidgetCondition(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11]);
        return new WidgetCondition(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16],v[17],v[18],v[19]);
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
