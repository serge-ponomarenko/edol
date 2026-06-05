package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.dto.MaintenanceStatusDto;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.service.MaintenanceService;
import org.spon.edolhub.service.PrinterStatsService;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceControllerTest {

    @Mock
    private MaintenanceService maintenanceService;

    @Mock
    private PrinterStatsService printerStatsService;

    @Mock
    private Model model;

    @InjectMocks
    private MaintenanceController controller;

    @Nested
    @DisplayName("maintenancePage")
    class MaintenancePage {

        @Test
        @DisplayName("returns maintenance view with stats")
        void returnsView() {
            MaintenanceStatusDto maintenance = new MaintenanceStatusDto();
            maintenance.setDue(true);
            maintenance.setId(1L);

            when(maintenanceService.getMaintenanceStatus()).thenReturn(List.of(maintenance));
            when(printerStatsService.getStats()).thenReturn(new PrinterStats());

            String view = controller.maintenancePage(model);

            assertThat(view).isEqualTo("dashboard/maintenance/list");
            verify(model).addAttribute("maintenances", List.of(maintenance));
            verify(model).addAttribute(eq("stats"), any(PrinterStats.class));
            verify(model).addAttribute("dueCount", 1L);
        }

        @Test
        @DisplayName("counts due items correctly")
        void countsDueItems() {
            MaintenanceStatusDto due1 = new MaintenanceStatusDto();
            due1.setDue(true);
            MaintenanceStatusDto due2 = new MaintenanceStatusDto();
            due2.setDue(true);
            MaintenanceStatusDto notDue = new MaintenanceStatusDto();
            notDue.setDue(false);

            when(maintenanceService.getMaintenanceStatus()).thenReturn(List.of(due1, notDue, due2));
            when(printerStatsService.getStats()).thenReturn(new PrinterStats());

            controller.maintenancePage(model);

            verify(model).addAttribute("dueCount", 2L);
        }
    }

    @Nested
    @DisplayName("completeMaintenance")
    class CompleteMaintenance {

        @Test
        @DisplayName("completes maintenance and redirects")
        void completesAndRedirects() {
            String view = controller.completeMaintenance(1L, "Fixed it");

            assertThat(view).isEqualTo("redirect:/maintenance");
            verify(maintenanceService).completeMaintenance(1L, "Fixed it");
        }

        @Test
        @DisplayName("completes maintenance with null notes")
        void completesWithNullNotes() {
            String view = controller.completeMaintenance(1L, null);

            assertThat(view).isEqualTo("redirect:/maintenance");
            verify(maintenanceService).completeMaintenance(1L, null);
        }
    }
}
