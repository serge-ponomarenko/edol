package org.spon.edolcore.service.printer.runtime;

public interface CoreRuntimeTenantExecutor {

    void execute(CoreRuntimeCatalogEntry entry, Runnable work);

    void execute(java.util.UUID printerId, Runnable work);
}
