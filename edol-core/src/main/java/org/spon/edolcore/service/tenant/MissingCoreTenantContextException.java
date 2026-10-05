package org.spon.edolcore.service.tenant;

public class MissingCoreTenantContextException extends IllegalStateException {

    public MissingCoreTenantContextException() {
        super("Core tenant context is required");
    }
}
