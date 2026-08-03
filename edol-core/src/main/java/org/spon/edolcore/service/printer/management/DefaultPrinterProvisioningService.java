package org.spon.edolcore.service.printer.management;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.controller.dto.printer.CreatePrinterRequest;
import org.spon.edolcore.controller.dto.printer.PrinterMapper;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultPrinterProvisioningService implements PrinterProvisioningService {

    private final PrinterRepository printerRepository;
    private final PrinterConnectionConfigurationRepository configurationRepository;
    private final PrinterMapper printerMapper;
    private final PrinterRuntimeLifecycleService runtimeLifecycleService;

    @Override
    public Printer createPrinter(CreatePrinterRequest request) {
        Printer printer = printerMapper.createPrinter(request);

        printer = printerRepository.save(printer);

        PrinterConnectionConfiguration connection =
                printerMapper.createConnection(printer, request.connection());

        configurationRepository.save(connection);

        runtimeLifecycleService.createRuntime(
                printer.getId()
        );

        runtimeLifecycleService.startRuntime(
                printer.getId()
        );

        return printer;
    }

}