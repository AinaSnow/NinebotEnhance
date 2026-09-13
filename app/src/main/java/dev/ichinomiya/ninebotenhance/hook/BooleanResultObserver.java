package dev.ichinomiya.ninebotenhance.hook;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Observe a Boolean synchronous/suspend result without changing values, context or failures. */
public final class BooleanResultObserver {
    private final AtomicBoolean completed = new AtomicBoolean();
    private final Consumer<Boolean> callback;
    public BooleanResultObserver(Consumer<Boolean> callback) { this.callback = callback; }
    public static boolean ownStateMachine(String owner, String method, Object continuation) {
        return continuation != null && continuation.getClass().getName().startsWith(owner + "$" + method + "$");
    }
    public void returned(Object value) {
        // COROUTINE_SUSPENDED, Kotlin Result.Failure and all other values never authorize creation.
        if (!(value instanceof Boolean) || !completed.compareAndSet(false, true)) return;
        try { callback.accept((Boolean) value); } catch (RuntimeException ignored) { /* Observation must not break the app. */ }
    }
    public Object continuation(Class<?> type, Object original) {
        if (!type.isInterface() || !type.isInstance(original)) throw new IllegalArgumentException("Not a continuation interface");
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result;
            try { result = method.invoke(original, args); }
            catch (InvocationTargetException e) { throw e.getCause(); }
            if (method.getName().equals("resumeWith") && args != null && args.length == 1) returned(args[0]);
            return result;
        });
    }
}
