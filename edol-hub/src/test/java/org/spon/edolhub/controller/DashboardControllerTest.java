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
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private PrinterService printerService;

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
            when(printerService.getState()).thenReturn(state);

            String view = controller.dashboard(model);

            assertThat(view).isEqualTo("dashboard/index");
            verify(model).addAttribute("printer", state);
            verify(model).addAttribute("printerOffline", false);
        }

        @Test
        @DisplayName("returns dashboard view when printer offline")
        void returnsViewWhenOffline() {
            when(printerService.getState()).thenReturn(null);

            String view = controller.dashboard(model);

            assertThat(view).isEqualTo("dashboard/index");
            verify(model).addAttribute(eq("printer"), any(PrinterState.class));
            verify(model).addAttribute("printerOffline", true);
        }
    }
}
