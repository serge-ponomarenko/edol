package org.spon.edolcore.service.printmetadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.spon.edol.model.Filament;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectSettingsParserService")
class ProjectSettingsParserServiceTest {

    private final ProjectSettingsParserService service = new ProjectSettingsParserService();

    private static final Path CONFIG_PATH = Path.of("src/test/resources/project_settings.config");

    private List<Filament> createTestFilaments() {
        List<Filament> filaments = new ArrayList<>();
        filaments.add(new Filament(1, "GFL99", "PLA", "#F95D73",
                null, null, 10.5, 31.2, true, false, null));
        filaments.add(new Filament(2, "GFL00", "PETG", "#00FF00",
                null, null, 5.2, 15.8, true, true, null));
        return filaments;
    }

    @Nested
    @DisplayName("enrichFilaments")
    class EnrichFilaments {

        @Test
        @DisplayName("enriches filaments with vendor, fullId, and amsMapping")
        void enrichesWithVendorAndFullId() {
            List<Filament> filaments = createTestFilaments();
            List<Integer> amsMapping = List.of(0, 1);

            service.enrichFilaments(CONFIG_PATH, filaments, amsMapping);

            assertThat(filaments.getFirst().getVendor()).isEqualTo("Generic PLA");
            assertThat(filaments.getFirst().getFullId()).isEqualTo("Generic PLA");
            assertThat(filaments.getFirst().getAmsSlot()).isZero();

            assertThat(filaments.get(1).getVendor()).isEqualTo("Generic PETG");
            assertThat(filaments.get(1).getFullId()).isEqualTo("Generic PETG");
            assertThat(filaments.get(1).getAmsSlot()).isEqualTo(1);
        }

        @Test
        @DisplayName("handles null amsMapping gracefully")
        void handlesNullAmsMapping() {
            List<Filament> filaments = createTestFilaments();

            service.enrichFilaments(CONFIG_PATH, filaments, null);

            assertThat(filaments.getFirst().getVendor()).isEqualTo("Generic PLA");
            assertThat(filaments.getFirst().getFullId()).isEqualTo("Generic PLA");
            assertThat(filaments.getFirst().getAmsSlot()).isNull();
        }

        @Test
        @DisplayName("strips @suffix from fullId")
        void stripsSuffixFromFullId() {
            List<Filament> filaments = createTestFilaments();

            service.enrichFilaments(CONFIG_PATH, filaments, null);

            assertThat(filaments.getFirst().getFullId()).doesNotContain("@");
        }

        @Test
        @DisplayName("returns silently when config lacks required keys")
        void handlesMissingConfigKeys() {
            Path invalidPath = Path.of("src/test/resources/missing_keys.config");
            List<Filament> filaments = createTestFilaments();

            service.enrichFilaments(invalidPath, filaments, null);

            assertThat(filaments.getFirst().getVendor()).isNull();
            assertThat(filaments.getFirst().getFullId()).isNull();
        }

        @Test
        @DisplayName("throws RuntimeException when config file does not exist")
        void throwsWhenFileMissing() {
            Path missing = Path.of("src/test/resources/nonexistent.config");
            List<Filament> filaments = createTestFilaments();

            org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> service.enrichFilaments(missing, filaments, null)
            );
        }
    }
}