package dev.ichinomiya.ninebotenhance.core;

/** Reject late callbacks from a retired binding, distinguish a requested bind from a live service, and count binds that never connected. */
public final class BindingState {
    private long generation, since;
    private boolean connected;
    private int failures;
    public synchronized long begin(long now) { if (generation > 0 && !connected) failures++; since = now; connected = false; return ++generation; }
    public synchronized boolean connected(long id) { if (id != generation) return false; connected = true; failures = 0; return true; }
    /** Consecutive bind attempts that were replaced without ever connecting; a live connection resets it. */
    public synchronized int failures() { return failures; }
    public synchronized boolean disconnected(long id, long now) {
        if (id != generation) return false;
        if (connected) since = now;
        connected = false; return true;
    }
    public synchronized boolean current(long id) { return id == generation; }
    public synchronized boolean expired(long now) { return !connected && now - since >= 5000; }
}
