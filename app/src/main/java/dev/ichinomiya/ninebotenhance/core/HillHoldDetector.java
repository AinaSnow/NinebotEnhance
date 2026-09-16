package dev.ichinomiya.ninebotenhance.core;

/**
 * Hill hold only counts once the speed/power condition has held continuously for the configured minimum time, and it only
 * ends once the condition has been absent (including stale readings) for the same time, so brief flickers change nothing.
 */
public final class HillHoldDetector {
    private long since;private boolean active;
    /** Evaluate the latest readings at the given time; returns whether hill hold is active. */
    public synchronized boolean update(RideState.Snapshot snapshot,long now,int powerMin,int powerMax,int speedMaxTenths,long minimumMs){
        boolean condition=snapshot!=null&&snapshot.hillHold(now,powerMin,powerMax,speedMaxTenths);
        if(condition==active){since=0;return active;}
        if(since==0||now<since)since=now;
        if(now-since>=minimumMs){active=condition;since=0;}
        return active;
    }
    public synchronized boolean active(){return active;}
    public synchronized void reset(){since=0;active=false;}
}
