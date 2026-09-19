package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.service.PrinterService;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.ui.Model;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private PrinterService printerService;

    @Mock
    private PrinterAccessService printerAccessService;

    @Mock
    private Model model;

    @InjectMocks
    private DashboardController controller;

    @Nested
    @DisplayName("dashboard")
    class Dashboard {

        @Test
        @DisplayName("returns dashboard view with printer state")
        void returnsViewWithState() {
            PrinterState state = new PrinterState();
            state.setCurrentTask("Test Print");
            when(printerService.getState(PRINTER_ID)).thenReturn(state);
            when(printerAccessService.getPrinters()).thenReturn(List.of());

            String view = controller.dashboard(PRINTER_ID, model);

            assertThat(view).isEqualTo("dashboard/index");
            verify(model).addAttribute("printer", state);
            verify(model).addAttribute("printerOffline", false);
        }

        @Test
        @DisplayName("returns dashboard view when printer offline")
        void returnsViewWhenOffline() {
            when(printerService.getState(PRINTER_ID)).thenReturn(null);
            when(printerAccessService.getPrinters()).thenReturn(List.of());

            String view = controller.dashboard(PRINTER_ID, model);

            assertThat(view).isEqualTo("dashboard/index");
            verify(model).addAttribute(eq("printer"), any(PrinterState.class));
            verify(model).addAttribute("printerOffline", true);
        }
    }
}
