package org.spon.edolcore.service.printer.runtime;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.model.metadata.MetadataRuntimeCoordinator;
import org.spon.edolcore.service.printer.telemetry.DefaultPrinterTelemetryProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultPrinterRuntimeLifecycleService
        implements PrinterRuntimeLifecycleService {

    private final PrinterRuntimeRegistry runtimeRegistry;
    private final MetadataRuntimeCoordinator metadataRuntimeCoordinator;
    private final DefaultPrinterTelemetryProvider telemetryProvider;
    private final PrinterRepository printerRepository;
    private final PrinterRuntimeQueryService runtimeQueryService;

    @Override
    public void createRuntime(UUID printerId) {
        runtimeRegistry.create(printerId);
    }

    @Override
    public void startRuntime(UUID printerId) {
        telemetryProvider.connect(printerId);
    }

    @Override
    public void stopRuntime(UUID printerId) {
        metadataRuntimeCoordinator.stop(printerId);
        telemetryProvider.disconnect(printerId);
    }

    @Override
    public void destroyRuntime(UUID printerId) {
        runtimeRegistry.remove(printerId);
    }

    @Override
    public void restartRuntime(UUID printerId) {
        if (runtimeQueryService.runtimeExists(printerId)) {
            stopRuntime(printerId);
            destroyRuntime(printerId);
        }

        reconcileRuntime(printerId);
    }

    @Override
    public void reconcileRuntime(UUID printerId) {
        Printer printer = printerRepository.findById(printerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Printer not found: " + printerId
                        ));

        boolean runtimeExists =
                runtimeQueryService.runtimeExists(printerId);

        if (printer.isEnabled()) {
            if (!runtimeExists) {
                createRuntime(printerId);
                startRuntime(printerId);
            }
        } else {
            if (runtimeExists) {
                stopRuntime(printerId);
                destroyRuntime(printerId);
            }
        }
    }

}