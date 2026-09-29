package org.spon.edolhub.config;

public final class HubSessionAttributes {

    public static final String ACTIVE_TENANT_ID = HubSessionAttributes.class.getName() + ".activeTenantId";
    public static final String AUTHENTICATED_AT = HubSessionAttributes.class.getName() + ".authenticatedAt";

    private HubSessionAttributes() {
    }
}
