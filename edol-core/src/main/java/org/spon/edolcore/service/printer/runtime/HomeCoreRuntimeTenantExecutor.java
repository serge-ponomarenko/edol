package org.spon.edolcore.service.printer.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
class HomeCoreRuntimeTenantExecutor implements CoreRuntimeTenantExecutor {

    @Override
    public void execute(CoreRuntimeCatalogEntry entry, Runnable work) {
        work.run();
    }

    @Override
    public void execute(java.util.UUID printerId, Runnable work) {
        work.run();
    }
}
