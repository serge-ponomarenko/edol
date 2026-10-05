package org.spon.edolcore.service.printer.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.controller.dto.printer.CreatePrinterRequest;
import org.spon.edolcore.controller.dto.printer.PrinterMapper;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterProvisioningRequest;
import org.spon.edolcore.persistence.printer.PrinterProvisioningRequestRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeLifecycleService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DefaultPrinterProvisioningService implements PrinterProvisioningService {

    private final PrinterRepository printerRepository;
    private final PrinterConnectionConfigurationRepository configurationRepository;
    private final PrinterProvisioningRequestRepository provisioningRequestRepository;
    private final PrinterMapper printerMapper;
    private final PrinterRuntimeLifecycleService runtimeLifecycleService;
    private final LogContextFactory logContextFactory;
    private final CoreTenantContext tenantContext;

    @Override
    public Printer createPrinter(CreatePrinterRequest request, UUID idempotencyKey) {
        if (tenantContext.hasCurrentTenant()) {
            if (idempotencyKey == null) {
                throw new IllegalArgumentException("An idempotency key is required for secure printer provisioning");
            }
            Printer existing = provisioningRequestRepository.findById(idempotencyKey)
                    .map(PrinterProvisioningRequest::getPrinter)
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        Printer printer = printerMapper.createPrinter(request);

        printer = printerRepository.save(printer);

        PrinterConnectionConfiguration connection =
                printerMapper.createConnection(printer, request.connection());

        configurationRepository.save(connection);

        if (tenantContext.hasCurrentTenant()) {
            provisioningRequestRepository.save(PrinterProvisioningRequest.builder()
                    .idempotencyKey(idempotencyKey)
                    .printer(printer)
                    .createdAt(Instant.now())
                    .build());
        }

        runtimeLifecycleService.reconcileRuntime(
                printer.getId()
        );

        logContextFactory
                .printer(
                        log.atInfo(),
                        printer.getId()
                )
                .log(
                        "Printer has been created: {}", printer.getDisplayId()
                );

        return printer;
    }

}
