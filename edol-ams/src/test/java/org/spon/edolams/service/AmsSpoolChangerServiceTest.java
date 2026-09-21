package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AmsSpoolChangerServiceTest {

    @Test
    void doesNotApplyOnePrinterStagedSpoolToAnotherPrinter() {
        RestClient hubClient = mock(RestClient.class);
        AmsSpoolChangerService service = new AmsSpoolChangerService(hubClient);
        UUID firstPrinter = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID secondPrinter = UUID.fromString("00000000-0000-0000-0000-000000000102");

        service.setSpoolScannedState(firstPrinter, 10L);
        service.setAmsSpoolIntoSlot(secondPrinter, 0);

        verifyNoInteractions(hubClient);
    }
}
