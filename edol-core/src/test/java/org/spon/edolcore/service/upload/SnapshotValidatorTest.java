package org.spon.edolcore.service.upload;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.config.UploadProperties;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotValidatorTest {
    @Test
    void acceptsDecodedJpegAndRejectsWrongFormatsOrTruncation() throws Exception {
        var validator = new SnapshotValidator(UploadFixtures.limits());
        byte[] jpeg = UploadFixtures.jpeg();
        assertThat(validator.read(new ByteArrayInputStream(jpeg))).isEqualTo(jpeg);
        for (byte[] invalid : new byte[][]{"not an image".getBytes(), Arrays.copyOf(jpeg, jpeg.length - 2),
                {(byte) 0xff, (byte) 0xd8, 0, 0, (byte) 0xff, (byte) 0xd9}}) {
            assertThatThrownBy(() -> validator.read(new ByteArrayInputStream(invalid)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(415));
        }
    }

    @Test
    void limitsStreamBytesWithoutDependingOnContentLength() {
        var limits = new UploadProperties(100_000, 100, 4096, 16384, 100, 1000);
        var validator = new SnapshotValidator(limits);
        assertThatThrownBy(() -> validator.read(new ByteArrayInputStream(new byte[101])))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(413));
    }

    @Test
    void rejectsExcessivePixelDimensionsBeforeDecodingTheRaster() throws Exception {
        var limits = new UploadProperties(100_000, 8192, 4096, 16384, 100, 63);
        var validator = new SnapshotValidator(limits);
        byte[] jpeg = UploadFixtures.jpeg();
        assertThatThrownBy(() -> validator.read(new ByteArrayInputStream(jpeg)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(415));
    }
}
