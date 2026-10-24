package org.spon.edolcore.service.model.transfer;

import org.junit.jupiter.api.Test;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.exception.ModelTransferException;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.ftps.CurlFtpsClient;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.springframework.context.ApplicationEventPublisher;
import java.nio.file.Files;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DirectModelTransferProviderTest {
    @Test
    void usesManagedConfigurationForUnicodeModelAndPublishesOnlyAfterTransfer() throws Exception {
        UUID id = UUID.randomUUID();
        var states = mock(PrinterStateService.class);
        var printer = new PrinterState();
        printer.setCurrentFile("Куб.gcode.3mf");
        when(states.getState(id)).thenReturn(printer);
        var management = mock(PrinterManagementService.class);
        var configuration = new PrinterConnectionConfiguration();
        configuration.setFtpHost("synthetic.invalid");
        configuration.setAccessCode("synthetic");
        configuration.setModelDirectory("/cache");
        when(management.getConnection(id)).thenReturn(configuration);
        var ftp = mock(CurlFtpsClient.class);
        var events = mock(ApplicationEventPublisher.class);
        var provider = new DirectModelTransferProvider(states, management, ftp, events);
        try (var files = mockStatic(Files.class)) {
            provider.requestModel(id);
            var order = inOrder(management, ftp, events);
            order.verify(management).getConnection(id);
            order.verify(ftp).download(any(), eq("/cache/Куб.gcode.3mf"), anyString());
            order.verify(events).publishEvent(any(Object.class));
        }
        reset(ftp, events);
        when(management.getConnection(id)).thenThrow(new IllegalArgumentException("Foreign tenant"));
        assertThatThrownBy(() -> provider.requestModel(id)).isInstanceOf(ModelTransferException.class);
        verifyNoInteractions(ftp, events);
    }
}
