package org.spon.edolcore.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.PrinterError;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.MqttEventPublisher;
import org.spon.edolcore.service.PrinterStateService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AmsEventListener")
class AmsEventListenerTest {

    @Mock
    private PrinterStateService printerStateService;

    @Mock
    private MqttEventPublisher mqttEventPublisher;

    private AmsEventListener listener;

    private final PrinterState printerState = new PrinterState();

    @Captor
    private ArgumentCaptor<Map<String, Object>> mapCaptor;

    @Nested
    @DisplayName("handleAmsEvent")
    class HandleAmsEvent {

        @Test
        @DisplayName("publishes ams.slot.unloaded with slot number")
        void slotUnloaded() {
            printerState.setError(new PrinterError(1, "error"));
            when(printerStateService.getState()).thenReturn(printerState);
            listener = new AmsEventListener(printerStateService, mqttEventPublisher);

            listener.handleAmsEvent(new AmsEvent(AmsEventType.AMS_SLOT_UNLOADED, 2));

            assertThat(printerState.getError()).isNull();
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/ams"), mapCaptor.capture());
            assertThat(mapCaptor.getValue()).containsEntry("event", "ams.slot.unloaded");
            assertThat(mapCaptor.getValue()).containsEntry("slot", 2);
        }

        @Test
        @DisplayName("publishes ams.slot.loaded with slot number")
        void slotLoaded() {
            when(printerStateService.getState()).thenReturn(printerState);
            listener = new AmsEventListener(printerStateService, mqttEventPublisher);

            listener.handleAmsEvent(new AmsEvent(AmsEventType.AMS_SLOT_LOADED, 0));

            assertThat(printerState.getError()).isNull();
            verify(mqttEventPublisher, timeout(1000)).publish(eq("edolcore/ams"), mapCaptor.capture());
            assertThat(mapCaptor.getValue()).containsEntry("event", "ams.slot.loaded");
            assertThat(mapCaptor.getValue()).containsEntry("slot", 0);
        }

        @Test
        @DisplayName("clears printer state error on any AMS event")
        void clearsError() {
            printerState.setError(new PrinterError(50348044, "Some error"));
            when(printerStateService.getState()).thenReturn(printerState);
            listener = new AmsEventListener(printerStateService, mqttEventPublisher);

            listener.handleAmsEvent(new AmsEvent(AmsEventType.AMS_SLOT_LOADED, 1));

            assertThat(printerState.getError()).isNull();
        }
    }
}