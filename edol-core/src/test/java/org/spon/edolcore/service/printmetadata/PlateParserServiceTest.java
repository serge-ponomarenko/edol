package org.spon.edolcore.service.printmetadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.spon.edol.model.BoundingBox;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PlateParserService")
class PlateParserServiceTest {

    private final PlateParserService service = new PlateParserService();

    private static final Path PLATE_JSON = Path.of("src/test/resources/plate.json");

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("parses bbox_objects and filters out wipe_tower")
        void parsesAndFiltersWipeTower() {
            List<BoundingBox> boxes = service.parse(PLATE_JSON);

            assertThat(boxes).hasSize(2);

            BoundingBox b1 = boxes.getFirst();
            assertThat(b1.getX1()).isEqualTo(10.0);
            assertThat(b1.getY1()).isEqualTo(20.0);
            assertThat(b1.getX2()).isEqualTo(110.0);
            assertThat(b1.getY2()).isEqualTo(120.0);

            BoundingBox b2 = boxes.get(1);
            assertThat(b2.getX1()).isEqualTo(130.0);
            assertThat(b2.getY1()).isEqualTo(20.0);
            assertThat(b2.getX2()).isEqualTo(230.0);
            assertThat(b2.getY2()).isEqualTo(120.0);
        }

        @Test
        @DisplayName("returns empty list when file has no bbox_objects")
        void returnsEmptyWhenNoBboxObjects() {
            Path emptyPath = Path.of("src/test/resources/empty_slice_info.config");

            assertThatThrownBy(() -> service.parse(emptyPath))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws RuntimeException when file does not exist")
        void throwsWhenFileMissing() {
            Path missing = Path.of("src/test/resources/nonexistent.json");

            assertThatThrownBy(() -> service.parse(missing))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}