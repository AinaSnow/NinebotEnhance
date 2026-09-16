package dev.ichinomiya.ninebotenhance.core;

/**
 * Live riding readings polled from the dashboard: speed (rSpeed, 0.1 km/h units) and motor power (rPower, a signed 16-bit
 * value in raw units of about a watt; regenerative braking reads negative). Hill hold (the dashboard's "H", 拧动油门解除坡道驻车) is inferred from the motor holding while the vehicle
 * stands still; see {@link HillHoldDetector} for the minimum-duration rule.
 */
public final class RideState {
    public static final String SPEED_COMMAND="rSpeed",POWER_COMMAND="rPower";
    /** Readings expire after this long without a fresh reply. */
    public static final long FRESH_MS=3000;
    /** Power before any reading. */
    public static final int NO_POWER=Integer.MIN_VALUE;
    /** The bus carries rPower as an unsigned 16-bit word; values above 32767 are negative (regeneration). */
    public static int signedPower(int raw){return (short)raw;}
    public record Snapshot(int speedTenths,int power,long speedAt,long powerAt){
        /** Both readings fresh, speed at most speedMaxTenths (0 = exactly standing still) and power within (powerMin, powerMax]. */
        public boolean hillHold(long now,int powerMin,int powerMax,int speedMaxTenths){
            return speedAt>0&&powerAt>0&&now-speedAt<=FRESH_MS&&now-powerAt<=FRESH_MS&&speedTenths>=0&&speedTenths<=speedMaxTenths&&power>powerMin&&power<=powerMax;
        }
        public boolean hillHold(long now,int powerMin,int speedMaxTenths){return hillHold(now,powerMin,WidgetSettings.DEFAULT_HOLD_POWER_MAX,speedMaxTenths);}
        public boolean hillHold(long now){return hillHold(now,WidgetSettings.DEFAULT_HOLD_POWER,WidgetSettings.DEFAULT_HOLD_POWER_MAX,WidgetSettings.DEFAULT_HOLD_SPEED*10);}
        public float speedKmh(){return speedTenths<0?-1:speedTenths/10f;}
        public boolean hasPower(){return powerAt>0&&power!=NO_POWER;}
    }
    private int speedTenths=-1,power=NO_POWER;private long speedAt,powerAt;
    public synchronized void speed(int tenths,long now){speedTenths=tenths;speedAt=now;}
    public synchronized void power(int raw,long now){power=raw;powerAt=now;}
    public synchronized void clear(){speedTenths=-1;power=NO_POWER;speedAt=powerAt=0;}
    public synchronized Snapshot snapshot(){return new Snapshot(speedTenths,power,speedAt,powerAt);}
}
