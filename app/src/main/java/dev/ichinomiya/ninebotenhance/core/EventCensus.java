package dev.ichinomiya.ninebotenhance.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded logging policy for high-rate observed events: per key the first few occurrences are logged in full, afterwards only
 * every N-th, and a periodic census line lists the counts of everything seen since the last census.
 */
public final class EventCensus {
    public static final int FULL_LOGS=3,SAMPLE_EVERY=50;
    private final Map<String,AtomicInteger> counts=new ConcurrentHashMap<>(),total=new ConcurrentHashMap<>();
    private final int fullLogs,sampleEvery;
    public EventCensus(){this(FULL_LOGS,SAMPLE_EVERY);}
    public EventCensus(int fullLogs,int sampleEvery){this.fullLogs=fullLogs;this.sampleEvery=sampleEvery;}
    /** Records one occurrence and says whether this one should be written to the log. */
    public boolean shouldLog(String key){
        counts.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();
        int n=total.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();
        return n<=fullLogs||n%sampleEvery==0;
    }
    public int seen(String key){AtomicInteger n=total.get(key);return n==null?0:n.get();}
    /** Counts since the previous census, sorted by key, then resets them; empty string when nothing was seen. */
    public String census(){
        List<String> keys=new ArrayList<>(counts.keySet());Collections.sort(keys);StringBuilder b=new StringBuilder();int sum=0;
        for(String key:keys){int n=counts.remove(key).get();if(n==0)continue;sum+=n;if(b.length()>0)b.append(',');b.append(key).append('=').append(n);}
        return sum==0?"":"{"+b+"} total="+sum;
    }
    public static String truncate(String value,int limit){
        if(value==null)return "null";String clean=value.replace('\n',' ').replace('\r',' ');
        return clean.length()<=limit?clean:clean.substring(0,limit)+"…("+clean.length()+")";
    }
}
