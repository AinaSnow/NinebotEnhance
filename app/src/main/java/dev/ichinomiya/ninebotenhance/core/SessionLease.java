package dev.ichinomiya.ninebotenhance.core;

import dev.ichinomiya.ninebotenhance.ipc.Protocol;

/** One display per user gesture. Cancelled handshakes and stale callbacks cannot resurrect it. */
public final class SessionLease {
    private String request, secret;
    private boolean attached, ready;
    public synchronized boolean begin(String id, String token) {
        if (request != null || !Protocol.validRequest(id) || !Protocol.validRequest(token)) return false;
        request = id; secret = token; attached = ready = false; return true;
    }
    public synchronized boolean owns(String id) { return request != null && request.equals(id); }
    public synchronized boolean attach(String token) {
        if (secret == null || !secret.equals(token) || attached) return false;
        attached = true; return true;
    }
    public synchronized boolean authorize(String token) { return attached && secret != null && secret.equals(token); }
    public synchronized boolean ready(String token) { if (!authorize(token)) return false; ready = true; return true; }
    public synchronized boolean isReady() { return ready && request != null; }
    public synchronized String request() { return request; }
    public synchronized boolean end(String id) {
        if (!owns(id)) return false;
        request = secret = null; attached = ready = false; return true;
    }
}
