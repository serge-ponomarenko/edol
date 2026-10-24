package org.spon.edolcore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.event.model.MetadataParsedEvent;
import org.spon.edolcore.event.model.MetadataParsedEventListener;
import org.spon.edolcore.service.mqtt.CoreIntegrationEventPublisher;
import org.spon.edolcore.service.print.recovery.StartupSynchronizationService;
import org.spon.edolcore.service.printer.runtime.PrinterRuntime;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeQueryService;
import org.springframework.context.ApplicationEventPublisher;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LivePrintingStateTest {
    private final PrinterRuntimeQueryService runtimes = mock(PrinterRuntimeQueryService.class);
    private final PrinterStateService states = new PrinterStateService(mock(ApplicationEventPublisher.class),
            mock(StartupSynchronizationService.class), runtimes, new LogContextFactory());
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void printingComesFromTelemetryBeforeMetadataAndSurvivesDeltaPackets() throws Exception {
        UUID printer = UUID.randomUUID();
        when(runtimes.getRuntime(printer)).thenReturn(new PrinterRuntime(printer));
        for (String gcode : new String[]{"PREPARE", "RUNNING", "PAUSE", "FINISH", "FAILED", "IDLE"}) {
            states.update(printer, json.readTree("{\"gcode_state\":\"" + gcode + "\",\"mc_percent\":58}"));
            boolean active = java.util.Set.of("PREPARE", "RUNNING", "PAUSE").contains(gcode);
            assertThat(states.getState(printer).isPrinting()).isEqualTo(active);
            assertThat(json.valueToTree(states.getState(printer)).get("printing").asBoolean()).isEqualTo(active);
            states.update(printer, json.readTree("{\"nozzle_temper\":230}"));
            assertThat(states.getState(printer).isPrinting()).isEqualTo(active);
        }
    }

    @Test
    void lateMetadataCannotResurrectFinishedPrintingAndLogicalPrintersStayIndependent() throws Exception {
        UUID direct = UUID.randomUUID(), agent = UUID.randomUUID();
        for (UUID id : new UUID[]{direct, agent}) {
            when(runtimes.getRuntime(id)).thenReturn(new PrinterRuntime(id));
            states.getState(id).setSessionId(UUID.randomUUID().toString());
            states.update(id, json.readTree("{\"gcode_state\":\"RUNNING\",\"mc_percent\":58}"));
        }
        states.update(direct, json.readTree("{\"gcode_state\":\"FINISH\"}"));
        new MetadataParsedEventListener(states, mock(CoreIntegrationEventPublisher.class))
                .handle(new MetadataParsedEvent(direct, "Куб.gcode.3mf"));
        assertThat(states.getState(direct).isPrinting()).isFalse();
        assertThat(states.getState(agent).isPrinting()).isTrue();
        assertThat(states.getState(agent).getProgress()).isEqualTo(58);
        assertThat(states.getState(direct).getSessionId()).isNotEqualTo(states.getState(agent).getSessionId());
    }
}
