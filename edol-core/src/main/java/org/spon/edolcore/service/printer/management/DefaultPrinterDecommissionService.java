package org.spon.edolcore.service.printer.management;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.printer.management.exception.PrinterNotFoundException;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultPrinterDecommissionService
        implements PrinterDecommissionService {

    private final PrinterRepository printerRepository;
    private final PrinterConnectionConfigurationRepository configurationRepository;
    private final PrinterRuntimeLifecycleService runtimeLifecycleService;

    @Override
    public void decommissionPrinter(UUID printerId) {
        Printer printer = printerRepository.findById(printerId)
                .orElseThrow(() ->
                        new PrinterNotFoundException(printerId));

        runtimeLifecycleService.stopRuntime(
                printerId
        );

        runtimeLifecycleService.destroyRuntime(
                printerId
        );

        configurationRepository.deleteByPrinterId(printerId);

        printerRepository.delete(printer);
    }

}
