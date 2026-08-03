package org.spon.edolcore.service.printer.runtime;

import java.util.UUID;

public interface PrinterRuntimeLifecycleService {

    void createRuntime(UUID printerId);

    void startRuntime(UUID printerId);

    void stopRuntime(UUID printerId);

    void destroyRuntime(UUID printerId);

    void restartRuntime(UUID printerId);

}