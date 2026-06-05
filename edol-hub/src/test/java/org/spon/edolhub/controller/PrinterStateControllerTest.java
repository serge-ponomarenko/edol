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
import org.spon.edolhub.service.PrinterDashboardStateService;
import org.spon.edolhub.service.PrinterStatsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterStateControllerTest {

    @Mock
    private PrinterStatsService printerStatsService;

    @Mock
    private MaintenanceService maintenanceService;

    @Mock
    private PrinterDashboardStateService printerDashboardStateService;

    @InjectMocks
    private PrinterStateController controller;

    @Nested
    @DisplayName("getState")
    class GetState {

        @Test
        @DisplayName("returns enriched printer state")
        void returnsEnrichedState() {
            PrinterStateController.PrinterStateEnriched expected = new PrinterStateController.PrinterStateEnriched();
            when(printerDashboardStateService.getState()).thenReturn(expected);

            PrinterStateController.PrinterStateEnriched result = controller.getState();

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("getStats")
    class GetStats {

        @Test
        @DisplayName("returns printer stats")
        void returnsStats() {
            PrinterStats stats = new PrinterStats();
            when(printerStatsService.getStats()).thenReturn(stats);

            PrinterStats result = controller.getStats();

            assertThat(result).isSameAs(stats);
        }
    }

    @Nested
    @DisplayName("getMaintenanceAlerts")
    class GetMaintenanceAlerts {

        @Test
        @DisplayName("returns due maintenance alerts only")
        void returnsDueAlerts() {
            MaintenanceStatusDto due1 = new MaintenanceStatusDto();
            due1.setDue(true);
            MaintenanceStatusDto due2 = new MaintenanceStatusDto();
            due2.setDue(true);
            MaintenanceStatusDto notDue = new MaintenanceStatusDto();
            notDue.setDue(false);

            when(maintenanceService.getMaintenanceStatus()).thenReturn(List.of(due1, notDue, due2));

            List<MaintenanceStatusDto> alerts = controller.getMaintenanceAlerts();

            assertThat(alerts)
                    .hasSize(2)
                    .containsExactly(due1, due2);
        }

        @Test
        @DisplayName("returns empty list when no due alerts")
        void returnsEmptyWhenNoneDue() {
            MaintenanceStatusDto notDue = new MaintenanceStatusDto();
            notDue.setDue(false);

            when(maintenanceService.getMaintenanceStatus()).thenReturn(List.of(notDue));

            List<MaintenanceStatusDto> alerts = controller.getMaintenanceAlerts();

            assertThat(alerts).isEmpty();
        }
    }
}
