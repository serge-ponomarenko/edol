package org.spon.edolcore.service.mqtt;

import java.util.Optional;
import java.util.UUID;

/** Resolves Core event tenancy only from persisted printer ownership. */
public interface CoreEventTenantResolver {

    Optional<UUID> tenantIdForPrinter(UUID printerId);
}
