package dev.ichinomiya.ninebotenhance.diagnostics;

import java.lang.ref.*;
import java.util.*;

/** Weak object identity without executing a target object's equals/hashCode methods. */
public final class WeakIdentityMap<V> {
    private static final class Key extends WeakReference<Object> {
        private final int hash;
        Key(Object value,ReferenceQueue<Object> queue) { super(value,queue);hash=System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) { return this==other || other instanceof Key && get()!=null && get()==((Key)other).get(); }
    }
    private final ReferenceQueue<Object> queue=new ReferenceQueue<>();
    private final Map<Key,V> values=new HashMap<>();
    private void clean() { Reference<?> key;while((key=queue.poll())!=null)values.remove(key); }
    public synchronized V get(Object key) { clean();return key==null?null:values.get(new Key(key,null)); }
    public synchronized void put(Object key,V value) { if(key==null)return;clean();values.put(new Key(key,queue),value); }
    public synchronized int size() { clean();return values.size(); }
}
