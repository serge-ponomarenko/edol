package org.spon.edolcore.service.printmetadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.spon.edol.model.Filament;
import org.spon.edol.model.PrintObject;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SliceInfoParserService")
class SliceInfoParserServiceTest {

    private final SliceInfoParserService service = new SliceInfoParserService();

    private static final Path SLICE_INFO = Path.of("src/test/resources/slice_info.config");
    private static final Path EMPTY_SLICE_INFO = Path.of("src/test/resources/empty_slice_info.config");

    @Nested
    @DisplayName("extractPlateIndex")
    class ExtractPlateIndex {

        @Test
        @DisplayName("extracts plate index from slice_info.config")
        void extractsPlateIndex() throws Exception {
            int index = service.extractPlateIndex(SLICE_INFO);

            assertThat(index).isEqualTo(3);
        }

        @Test
        @DisplayName("throws RuntimeException when XML has no plate metadata")
        void throwsWhenNoPlateIndex() {
            Path path = Path.of("src/test/resources/no_plate_index.config");

            assertThatThrownBy(() -> service.extractPlateIndex(path))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws RuntimeException when file does not exist")
        void throwsWhenFileMissing() {
            Path missing = Path.of("src/test/resources/nonexistent.config");

            assertThatThrownBy(() -> service.extractPlateIndex(missing))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("parseFilaments")
    class ParseFilaments {

        @Test
        @DisplayName("parses filament list from slice_info.config")
        void parsesFilaments() {
            List<Filament> filaments = service.parseFilaments(SLICE_INFO);

            assertThat(filaments).hasSize(2);

            Filament f1 = filaments.getFirst();
            assertThat(f1.getId()).isEqualTo(1);
            assertThat(f1.getFilamentBrandIndex()).isEqualTo("GFL99");
            assertThat(f1.getType()).isEqualTo("PLA");
            assertThat(f1.getColor()).isEqualTo("#F95D73");
            assertThat(f1.getUsedMeters()).isEqualTo(10.5);
            assertThat(f1.getUsedGrams()).isEqualTo(31.2);
            assertThat(f1.isUsedForObject()).isTrue();
            assertThat(f1.isUsedForSupport()).isFalse();

            Filament f2 = filaments.get(1);
            assertThat(f2.getId()).isEqualTo(2);
            assertThat(f2.getFilamentBrandIndex()).isEqualTo("GFL00");
            assertThat(f2.getType()).isEqualTo("PETG");
            assertThat(f2.getColor()).isEqualTo("#00FF00");
            assertThat(f2.getUsedMeters()).isEqualTo(5.2);
            assertThat(f2.getUsedGrams()).isEqualTo(15.8);
            assertThat(f2.isUsedForObject()).isTrue();
            assertThat(f2.isUsedForSupport()).isTrue();
        }

        @Test
        @DisplayName("returns empty list when no filament elements")
        void returnsEmptyWhenNoFilaments() {
            List<Filament> filaments = service.parseFilaments(EMPTY_SLICE_INFO);

            assertThat(filaments).isEmpty();
        }
    }

    @Nested
    @DisplayName("parsePrintObjects")
    class ParsePrintObjects {

        @Test
        @DisplayName("parses print objects from slice_info.config")
        void parsesPrintObjects() {
            List<PrintObject> objects = service.parsePrintObjects(SLICE_INFO);

            assertThat(objects).hasSize(3);

            PrintObject o1 = objects.getFirst();
            assertThat(o1.getId()).isEqualTo(127);
            assertThat(o1.getName()).isEqualTo("result (1).obj_A");
            assertThat(o1.isSkipped()).isFalse();

            PrintObject o2 = objects.get(1);
            assertThat(o2.getId()).isEqualTo(128);
            assertThat(o2.getName()).isEqualTo("result (2).obj_B");
            assertThat(o2.isSkipped()).isTrue();

            PrintObject o3 = objects.get(2);
            assertThat(o3.getId()).isEqualTo(129);
            assertThat(o3.getName()).isEqualTo("wipe_tower");
            assertThat(o3.isSkipped()).isFalse();
        }

        @Test
        @DisplayName("returns empty list when no object elements")
        void returnsEmptyWhenNoObjects() {
            List<PrintObject> objects = service.parsePrintObjects(EMPTY_SLICE_INFO);

            assertThat(objects).isEmpty();
        }
    }
}