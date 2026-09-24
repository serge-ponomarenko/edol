package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.FilamentSpool;
import org.spon.edolhub.model.entity.JobSpoolUsage;
import org.spon.edolhub.model.entity.PrintJob;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.model.entity.Tenant;
import org.spon.edolhub.repository.JobSpoolUsageRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSpoolUsageServiceTest {

    @Mock
    private JobSpoolUsageRepository jobSpoolUsageRepository;

    @InjectMocks
    private JobSpoolUsageService jobSpoolUsageService;

    @Captor
    private ArgumentCaptor<JobSpoolUsage> usageCaptor;

    @Test
    void createsUsageWithThePrintJobTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Printer printer = new Printer();
        printer.setTenant(tenant);
        PrintJob printJob = new PrintJob();
        printJob.setPrinter(printer);
        FilamentSpool spool = new FilamentSpool();
        when(jobSpoolUsageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        JobSpoolUsage usage = jobSpoolUsageService.create(printJob, spool);

        verify(jobSpoolUsageRepository).save(usageCaptor.capture());
        assertThat(usageCaptor.getValue().getTenant()).isEqualTo(tenant);
        assertThat(usage.getFilamentSpool()).isEqualTo(spool);
    }
}
