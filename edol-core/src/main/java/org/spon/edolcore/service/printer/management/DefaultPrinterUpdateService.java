package org.spon.edolcore.service.printer.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.controller.dto.printer.PrinterMapper;
import org.spon.edolcore.controller.dto.printer.UpdatePrinterConnectionRequest;
import org.spon.edolcore.controller.dto.printer.UpdatePrinterRequest;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultPrinterUpdateService implements PrinterUpdateService {

    private final PrinterRepository printerRepository;
    private final PrinterConnectionConfigurationRepository configurationRepository;
    private final PrinterMapper printerMapper;
    private final PrinterRuntimeLifecycleService printerRuntimeLifecycleService;
    private final LogContextFactory logContextFactory;

    @Override
    public Printer updatePrinter(UUID printerId,
                                 UpdatePrinterRequest request) {
        Printer printer = printerRepository.findById(printerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Printer not found: " + printerId
                        ));

        printerMapper.updatePrinter(printer, request);

        printerRuntimeLifecycleService.restartRuntime(
                printer.getId()
        );

        logContextFactory
                .printer(
                        log.atInfo(),
                        printer.getId()
                )
                .log(
                        "Printer has been updated: {}", printer.getDisplayId()
                );

        return printerRepository.save(printer);
    }

    @Override
    public PrinterConnectionConfiguration updateConnection(
            UUID printerId,
            UpdatePrinterConnectionRequest request) {
        PrinterConnectionConfiguration configuration =
                configurationRepository.findByPrinterId(printerId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Printer not found: " + printerId
                                ));

        printerMapper.updateConnection(configuration, request);

        printerRuntimeLifecycleService.restartRuntime(
                printerId
        );

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Printer configuration has been updated: {}", printerId
                );

        return configurationRepository.save(configuration);
    }

}
