package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.entity.PrintJob;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRuntimeStateServiceTest {

    private final PrintRuntimeStateService service = new PrintRuntimeStateService();

    @Test
    void isolatesRuntimeStateByPrinter() {
        UUID firstPrinter = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID secondPrinter = UUID.fromString("00000000-0000-0000-0000-000000000102");
        PrintJob firstJob = new PrintJob();
        PrintJob secondJob = new PrintJob();

        service.setCurrentJob(firstPrinter, firstJob);
        service.setAllocationPreviewReady(firstPrinter, true);
        service.setCurrentJob(secondPrinter, secondJob);

        assertThat(service.getCurrentJob(firstPrinter)).isSameAs(firstJob);
        assertThat(service.isAllocationPreviewReady(firstPrinter)).isTrue();
        assertThat(service.getCurrentJob(secondPrinter)).isSameAs(secondJob);
        assertThat(service.isAllocationPreviewReady(secondPrinter)).isFalse();

        service.clear(firstPrinter);

        assertThat(service.getCurrentJob(firstPrinter)).isNull();
        assertThat(service.getCurrentJob(secondPrinter)).isSameAs(secondJob);
    }
}
