package org.spon.edolhub.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Holds only a tenant identity established by a trusted ingress boundary.
 */
@Service
public class TenantContext {

    private final ThreadLocal<ScopeState> state = new ThreadLocal<>();

    public boolean hasCurrentTenant() {
        return state.get() != null;
    }

    public UUID getCurrentTenantId() {
        ScopeState current = state.get();
        if (current == null) {
            throw new MissingTenantContextException();
        }
        return current.tenantId();
    }

    public TenantScope open(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required");
        }

        ScopeState current = state.get();
        if (current == null) {
            state.set(new ScopeState(tenantId, 1));
            return new TenantScope(this, tenantId);
        }
        if (!current.tenantId().equals(tenantId)) {
            throw new IllegalStateException("Cannot replace an active tenant context");
        }

        state.set(new ScopeState(tenantId, current.depth() + 1));
        return new TenantScope(this, tenantId);
    }

    private void close(UUID tenantId) {
        ScopeState current = state.get();
        if (current == null || !current.tenantId().equals(tenantId)) {
            throw new IllegalStateException("Tenant context scope was closed out of order");
        }
        if (current.depth() == 1) {
            state.remove();
        } else {
            state.set(new ScopeState(tenantId, current.depth() - 1));
        }
    }

    private record ScopeState(UUID tenantId, int depth) {
    }

    public static final class TenantScope implements AutoCloseable {

        private final TenantContext context;
        private final UUID tenantId;
        private boolean closed;

        private TenantScope(TenantContext context, UUID tenantId) {
            this.context = context;
            this.tenantId = tenantId;
        }

        @Override
        public void close() {
            if (!closed) {
                context.close(tenantId);
                closed = true;
            }
        }
    }
}
