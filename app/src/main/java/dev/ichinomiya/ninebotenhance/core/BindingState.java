package dev.ichinomiya.ninebotenhance.core;

/** Reject late callbacks from a retired binding, and distinguish a requested bind from a live service. */
public final class BindingState {
    private long generation, since;
    private boolean connected;
    public synchronized long begin(long now) { since = now; connected = false; return ++generation; }
    public synchronized boolean connected(long id) { if (id != generation) return false; connected = true; return true; }
    public synchronized boolean disconnected(long id, long now) {
        if (id != generation) return false;
        if (connected) since = now;
        connected = false; return true;
    }
    public synchronized boolean current(long id) { return id == generation; }
    public synchronized boolean expired(long now) { return !connected && now - since >= 5000; }
}
