package com.careercompass.common.observability;

import java.util.Optional;
import java.util.UUID;

public final class RequestCorrelationContext {

    private static final ThreadLocal<UUID> REQUEST_ID = new ThreadLocal<>();

    private RequestCorrelationContext() {
    }

    public static void set(UUID requestId) {
        REQUEST_ID.set(requestId);
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(REQUEST_ID.get());
    }

    public static UUID currentOrCreate() {
        return current().orElseGet(UUID::randomUUID);
    }

    public static void clear() {
        REQUEST_ID.remove();
    }
}
