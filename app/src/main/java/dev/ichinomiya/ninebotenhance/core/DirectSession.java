package dev.ichinomiya.ninebotenhance.core;

import dev.ichinomiya.ninebotenhance.ipc.Protocol;

/** One user gesture. Vehicle checks precede display creation; stale callbacks cannot take ownership. */
public final class DirectSession {
    public enum Phase { IDLE, CHECKING_VEHICLE, CONSENT, WAITING_FRAMES, STARTING, RUNNING }
    public enum Mode { VEHICLE, LOCAL }
    private volatile Phase phase = Phase.IDLE;
    private volatile String request;
    private volatile Mode mode;
    private Boolean vehiclePower;
    private boolean cruiseReady;
    public boolean begin(String id) {
        return begin(id, Mode.VEHICLE);
    }
    public synchronized boolean begin(String id, Mode mode) {
        if (phase != Phase.IDLE || !Protocol.validRequest(id) || mode == null) return false;
        this.mode = mode; request = id; vehiclePower = null; cruiseReady = false;
        phase = mode == Mode.VEHICLE ? Phase.CHECKING_VEHICLE : Phase.CONSENT; return true;
    }
    public boolean matches(String id) { return request != null && request.equals(id); }
    /** Atomic phase/request snapshot for a query entering on a non-UI thread. */
    public synchronized String vehicleCheckRequest() { return phase == Phase.CHECKING_VEHICLE ? request : null; }
    public boolean powerChecked(String id, boolean on) {
        if (!matches(id) || phase != Phase.CHECKING_VEHICLE) return false;
        // An observed rejection cannot be superseded by another concurrent query.
        if (!Boolean.FALSE.equals(vehiclePower)) vehiclePower = on;
        return true;
    }
    public boolean cruiseReady(String id) {
        if (!matches(id) || phase != Phase.CHECKING_VEHICLE) return false;
        cruiseReady = true; return true;
    }
    public boolean prepareDisplay(String id) {
        if (!matches(id) || phase != Phase.CHECKING_VEHICLE || !Boolean.TRUE.equals(vehiclePower) || !cruiseReady) return false;
        phase = Phase.CONSENT; return true;
    }
    public boolean granted(String id) {
        if (!matches(id) || phase != Phase.CONSENT) return false;
        phase = Phase.WAITING_FRAMES; return true;
    }
    public boolean launch(String id) {
        if (!matches(id) || mode != Mode.VEHICLE || phase != Phase.WAITING_FRAMES) return false;
        phase = Phase.STARTING; return true;
    }
    public boolean running(String id) {
        if (!matches(id) || mode != Mode.VEHICLE || phase != Phase.STARTING) return false;
        phase = Phase.RUNNING; return true;
    }
    public boolean localReady(String id) {
        if (!matches(id) || mode != Mode.LOCAL || phase != Phase.WAITING_FRAMES) return false;
        phase = Phase.RUNNING; return true;
    }
    public synchronized boolean end(String id) {
        if (!matches(id)) return false;
        request = null; mode = null; phase = Phase.IDLE; return true;
    }
    public String request() { return request; }
    public Phase phase() { return phase; }
    public Mode mode() { return mode; }
    public boolean isLocal() { return mode == Mode.LOCAL; }
}
