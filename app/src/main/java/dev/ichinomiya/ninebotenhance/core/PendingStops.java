package dev.ichinomiya.ninebotenhance.core;

import dev.ichinomiya.ninebotenhance.ipc.Protocol;

import java.util.LinkedHashMap;

/** Retain cancellations until the matching request has been acknowledged by the broker. */
public final class PendingStops {
    public static final class Entry {
        public final String request;
        private Entry(String request) { this.request = request; }
    }
    private final LinkedHashMap<String, Entry> ids = new LinkedHashMap<>();
    public synchronized void add(String id) { if (Protocol.validRequest(id)) ids.put(id, new Entry(id)); }
    public synchronized Entry first() { return ids.isEmpty() ? null : ids.values().iterator().next(); }
    // A late BEGIN can require another STOP while the first STOP is in flight. Its old ACK must not erase the retry.
    public synchronized void acknowledged(Entry entry) { if (entry != null && ids.get(entry.request) == entry) ids.remove(entry.request); }
    public synchronized int size() { return ids.size(); }
}
