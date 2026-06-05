package org.spon.edolhub.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.MaintenanceDefinition;
import org.spon.edolhub.model.entity.PrinterStats;
import org.spon.edolhub.repository.MaintenanceDefinitionRepository;
import org.spon.edolhub.service.PrinterStatsService;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceConfigControllerTest {

    @Mock
    private MaintenanceDefinitionRepository repository;

    @Mock
    private PrinterStatsService printerStatsService;

    @Mock
    private Model model;

    @InjectMocks
    private MaintenanceConfigController controller;

    @Nested
    @DisplayName("configPage")
    class ConfigPage {

        @Test
        @DisplayName("returns config view with definitions")
        void returnsView() {
            List<MaintenanceDefinition> defs = List.of(new MaintenanceDefinition());
            when(repository.findAll()).thenReturn(defs);

            String view = controller.configPage(model);

            assertThat(view).isEqualTo("dashboard/maintenance/config");
            verify(model).addAttribute("definitions", defs);
        }
    }

    @Nested
    @DisplayName("createForm")
    class CreateForm {

        @Test
        @DisplayName("returns config form view with new definition")
        void returnsForm() {
            String view = controller.createForm(model);

            assertThat(view).isEqualTo("dashboard/maintenance/config-form");
            verify(model).addAttribute(eq("definition"), any(MaintenanceDefinition.class));
        }
    }

    @Nested
    @DisplayName("edit")
    class Edit {

        @Test
        @DisplayName("returns form view with existing definition")
        void returnsForm() {
            MaintenanceDefinition def = new MaintenanceDefinition();
            def.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(def));

            String view = controller.edit(1L, model);

            assertThat(view).isEqualTo("dashboard/maintenance/config-form");
            verify(model).addAttribute("definition", def);
        }
    }

    @Nested
    @DisplayName("addMaintenance")
    class AddMaintenance {

        @Test
        @DisplayName("saves definition and redirects")
        void savesAndRedirects() {
            MaintenanceDefinition def = new MaintenanceDefinition();
            def.setName("Test");

            String view = controller.addMaintenance(def);

            assertThat(view).isEqualTo("redirect:/maintenance/config");
            verify(repository).save(def);
        }
    }

    @Nested
    @DisplayName("deleteMaintenance")
    class DeleteMaintenance {

        @Test
        @DisplayName("deletes definition and redirects")
        void deletesAndRedirects() {
            String view = controller.deleteMaintenance(1L);

            assertThat(view).isEqualTo("redirect:/maintenance/config");
            verify(repository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("toggles active flag")
        void togglesActive() {
            MaintenanceDefinition def = new MaintenanceDefinition();
            def.setId(1L);
            def.setActive(true);
            when(repository.findById(1L)).thenReturn(Optional.of(def));

            String view = controller.toggle(1L);

            assertThat(view).isEqualTo("redirect:/maintenance/config");
            assertThat(def.isActive()).isFalse();
            verify(repository).save(def);
        }

        @Test
        @DisplayName("toggles inactive to active")
        void togglesInactive() {
            MaintenanceDefinition def = new MaintenanceDefinition();
            def.setId(1L);
            def.setActive(false);
            when(repository.findById(1L)).thenReturn(Optional.of(def));

            controller.toggle(1L);

            assertThat(def.isActive()).isTrue();
            verify(repository).save(def);
        }
    }

    @Nested
    @DisplayName("statsPage")
    class StatsPage {

        @Test
        @DisplayName("returns printer stats form view")
        void returnsView() {
            PrinterStats stats = new PrinterStats();
            when(printerStatsService.getStats()).thenReturn(stats);

            String view = controller.statsPage(model);

            assertThat(view).isEqualTo("dashboard/maintenance/printer-stats-form");
            verify(model).addAttribute("stats", stats);
        }
    }

    @Nested
    @DisplayName("updateStats")
    class UpdateStats {

        @Test
        @DisplayName("updates stats and redirects")
        void updatesAndRedirects() {
            PrinterStats stats = new PrinterStats();

            String view = controller.updateStats(stats);

            assertThat(view).isEqualTo("redirect:/maintenance/config");
            verify(printerStatsService).updateStats(stats);
        }
    }
}
