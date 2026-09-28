package org.spon.edolhub.service;

public class MissingTenantContextException extends IllegalStateException {

    public MissingTenantContextException() {
        super("Tenant context is required");
    }
}
