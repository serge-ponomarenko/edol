package org.spon.edolnotify.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotifyTenantContext {

    private final ThreadLocal<UUID> tenantId = new ThreadLocal<>();

    public Scope open(UUID value) {
        if (value == null || tenantId.get() != null) {
            throw new IllegalStateException("Notify tenant context is invalid");
        }
        tenantId.set(value);
        return new Scope(value);
    }

    public UUID currentTenantId() {
        UUID value = tenantId.get();
        if (value == null) {
            throw new IllegalStateException("Notify tenant context is required");
        }
        return value;
    }

    public final class Scope implements AutoCloseable {
        private final UUID value;
        private boolean closed;

        private Scope(UUID value) {
            this.value = value;
        }

        @Override
        public void close() {
            if (!closed) {
                if (!value.equals(tenantId.get())) {
                    throw new IllegalStateException("Notify tenant context was closed out of order");
                }
                tenantId.remove();
                closed = true;
            }
        }
    }
}
