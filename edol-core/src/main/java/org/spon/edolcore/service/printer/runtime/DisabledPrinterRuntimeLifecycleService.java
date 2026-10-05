package org.spon.edolcore.service.printer.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Keeps HTTP provisioning available while an explicitly isolated smoke profile disables device runtime work. */
@Service
@ConditionalOnProperty(name = "edol-core.runtime.printer-runtime-enabled", havingValue = "false")
public class DisabledPrinterRuntimeLifecycleService implements PrinterRuntimeLifecycleService {

    @Override
    public void createRuntime(UUID printerId) {
        // Intentionally disabled for the isolated smoke runtime.
    }

    @Override
    public void startRuntime(UUID printerId) {
        // Intentionally disabled for the isolated smoke runtime.
    }

    @Override
    public void stopRuntime(UUID printerId) {
        // Intentionally disabled for the isolated smoke runtime.
    }

    @Override
    public void destroyRuntime(UUID printerId) {
        // Intentionally disabled for the isolated smoke runtime.
    }

    @Override
    public void restartRuntime(UUID printerId) {
        // Intentionally disabled for the isolated smoke runtime.
    }

    @Override
    public void reconcileRuntime(UUID printerId) {
        // Intentionally disabled for the isolated smoke runtime.
    }
}
