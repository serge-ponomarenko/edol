package org.spon.edolcore.service.printer.management;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.persistence.printer.PrinterProvisioningRequest;
import org.spon.edolcore.persistence.printer.PrinterProvisioningRequestRepository;
import org.spon.edolcore.persistence.printer.PrinterRepository;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeLifecycleService;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.controller.dto.printer.PrinterMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultPrinterProvisioningServiceTest {

    @Test
    void returnsExistingProvisioningResultForTheSameIdempotencyKey() {
        PrinterRepository printerRepository = mock(PrinterRepository.class);
        PrinterConnectionConfigurationRepository configurationRepository = mock(PrinterConnectionConfigurationRepository.class);
        PrinterProvisioningRequestRepository provisioningRequestRepository = mock(PrinterProvisioningRequestRepository.class);
        PrinterMapper printerMapper = mock(PrinterMapper.class);
        PrinterRuntimeLifecycleService runtimeLifecycleService = mock(PrinterRuntimeLifecycleService.class);
        LogContextFactory logContextFactory = mock(LogContextFactory.class);
        CoreTenantContext tenantContext = new CoreTenantContext();
        DefaultPrinterProvisioningService service = new DefaultPrinterProvisioningService(
                printerRepository,
                configurationRepository,
                provisioningRequestRepository,
                printerMapper,
                runtimeLifecycleService,
                logContextFactory,
                tenantContext
        );
        UUID idempotencyKey = UUID.randomUUID();
        Printer existing = Printer.builder().id(UUID.randomUUID()).build();
        when(provisioningRequestRepository.findById(idempotencyKey)).thenReturn(Optional.of(
                PrinterProvisioningRequest.builder().idempotencyKey(idempotencyKey).printer(existing).build()
        ));

        Printer result;
        try (CoreTenantContext.TenantScope ignored = tenantContext.open(UUID.randomUUID())) {
            result = service.createPrinter(null, idempotencyKey);
        }

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(printerRepository, configurationRepository, printerMapper, runtimeLifecycleService, logContextFactory);
    }
}
