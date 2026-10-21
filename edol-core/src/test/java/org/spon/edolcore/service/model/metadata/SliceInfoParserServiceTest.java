package org.spon.edolcore.service.model.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spon.edolcore.exception.SliceInfoParseException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SliceInfoParserServiceTest {
    @TempDir
    Path temporary;
    private final SliceInfoParserService parser = new SliceInfoParserService();

    @Test
    void parsesOrdinaryMetadataWithTheHardenedBuilder() throws Exception {
        Path file = Files.writeString(temporary.resolve("slice_info.config"),
                "<config><plate><metadata key=\"index\" value=\"1\"/></plate></config>");
        assertThat(parser.extractPlateIndex(file)).isEqualTo(1);
        assertThat(parser.parseFilaments(file)).isEmpty();
        assertThat(parser.parsePrintObjects(file)).isEmpty();
    }

    @Test
    void rejectsDoctypeAndExternalEntitiesInEveryParserEntryPoint() throws Exception {
        Path file = Files.writeString(temporary.resolve("slice_info.config"),
                "<!DOCTYPE config [<!ENTITY external SYSTEM \"file:///never-read-synthetic-fixture\">]>"
                        + "<config>&external;</config>");
        assertThatThrownBy(() -> parser.extractPlateIndex(file)).isInstanceOf(SliceInfoParseException.class);
        assertThatThrownBy(() -> parser.parseFilaments(file)).isInstanceOf(SliceInfoParseException.class);
        assertThatThrownBy(() -> parser.parsePrintObjects(file)).isInstanceOf(SliceInfoParseException.class);
    }
}
