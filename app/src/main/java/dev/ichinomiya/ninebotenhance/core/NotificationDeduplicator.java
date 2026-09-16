package dev.ichinomiya.ninebotenhance.core;

import java.util.*;

/** Message identity survives app-wide notification refreshes and repeated conversation snapshots. */
public final class NotificationDeduplicator {
    public record Message(long timestamp, String sender, String text) {}
    private record Post(long timestamp, String title, String text) {}
    private static final class Source {
        Post last;
        final LinkedHashMap<Message,Integer> seen=new LinkedHashMap<>();
        long newest=Long.MIN_VALUE;
    }
    private final LinkedHashMap<String, Source> sources = new LinkedHashMap<>();

    /** Fallback for notifications without MessagingStyle; timestamp is Notification.when, never postTime. */
    public boolean accept(String key, long timestamp, String title, String text) {
        Source source=source(key);Post post = new Post(timestamp, title, text);
        if (post.equals(source.last)) return false;
        source.last=post;
        return true;
    }
    public List<Message> acceptMessages(String key,List<Message> messages) {
        if(messages.isEmpty())return List.of();
        Source source=source(key);boolean first=source.seen.isEmpty();long previousNewest=source.newest;
        Map<Message,Integer> counts=new HashMap<>();List<Message> added=new ArrayList<>();
        for(Message message:messages){
            int occurrence=counts.merge(message,1,Integer::sum);
            if(occurrence>source.seen.getOrDefault(message,0)&&message.timestamp()>=previousNewest)added.add(message);
            source.newest=Math.max(source.newest,message.timestamp());
        }
        for(Message message:messages){
            source.seen.put(message,Math.max(source.seen.getOrDefault(message,0),counts.get(message)));
            while(source.seen.size()>64)source.seen.remove(source.seen.keySet().iterator().next());
        }
        // A first snapshot can include unread history. Remember it, but initially show only the newest item.
        if(first&&added.size()>1)return List.of(added.get(added.size()-1));
        return List.copyOf(added);
    }
    private Source source(String key){
        Source source=sources.computeIfAbsent(key,k->new Source());
        while(sources.size()>128)sources.remove(sources.keySet().iterator().next());
        return source;
    }
    public boolean remove(String key) { return sources.remove(key) != null; }
    public void clear() { sources.clear(); }
}
