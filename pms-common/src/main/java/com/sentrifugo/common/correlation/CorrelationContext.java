package com.sentrifugo.common.correlation;

/**
 * Request correlation id — generated once per inbound request by
 * {@link CorrelationIdFilter} and readable anywhere on that request's thread.
 * Java equivalent of the Python services' {@code correlation.py} ContextVar.
 */
public final class CorrelationContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
    }

    /** The current request's correlation id, or "" outside a request (background jobs). */
    public static String get() {
        String value = CURRENT.get();
        return value != null ? value : "";
    }

    public static void clear() {
        CURRENT.remove();
    }
}
