package org.spon.edolnotify.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.service.MessageService;
import org.spon.edolnotify.service.PrinterService;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttEventListenerTest {

    private static final UUID FIRST_PRINTER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SECOND_PRINTER = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Mock
    private PrinterService printerService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MqttEventListener listener;

    @Test
    void routesNotificationsToThePrinterDeclaredByEachMqttEvent() {
        when(printerService.getState(FIRST_PRINTER)).thenReturn(new PrinterState());
        when(printerService.getState(SECOND_PRINTER)).thenReturn(new PrinterState());
        ReflectionTestUtils.setField(listener, "telegramProgressMessageStep", 10);

        listener.handle(new GenericMessage<>("{\"event\":\"printer.online\",\"printerId\":\"" + FIRST_PRINTER + "\"}"));
        listener.handle(new GenericMessage<>("{\"event\":\"printer.offline\",\"printerId\":\"" + SECOND_PRINTER + "\"}"));

        verify(messageService).sendPrinterOnlineMessage(FIRST_PRINTER);
        verify(messageService).sendPrinterOfflineMessage(SECOND_PRINTER);
    }
}
