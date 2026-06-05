package org.spon.edolcore.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edol.model.CameraSnapshot;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.camera.TimelapseService;
import org.spon.edolcore.service.printmetadata.ModelService;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CameraController")
class CameraControllerTest {

    @Mock
    private CameraSnapshotStore store;

    @Mock
    private TimelapseService timelapseService;

    @Mock
    private PrinterStatusImageService printerStatusImageService;

    @Mock
    private ModelService modelService;

    @InjectMocks
    private CameraController controller;

    @Nested
    @DisplayName("latest")
    class Latest {

        @Test
        @DisplayName("returns latest snapshot image bytes when available")
        void returnsLatestImage() {
            byte[] expected = "test-image-data".getBytes();
            CameraSnapshot snapshot = new CameraSnapshot(expected);
            when(store.getLatest()).thenReturn(snapshot);

            byte[] result = controller.latest();

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("returns empty byte array when no snapshot available")
        void returnsEmptyWhenNoSnapshot() {
            when(store.getLatest()).thenReturn(null);

            byte[] result = controller.latest();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLatestStatusImagePath")
    class GetLatestStatusImagePath {

        @Test
        @DisplayName("returns absolute path when metadata loaded and status image exists")
        void returnsPathWhenAvailable() {
            when(modelService.isMetadataLoaded()).thenReturn(true);
            when(printerStatusImageService.getStatusImage())
                    .thenReturn(new File("/tmp/test_status.jpg"));

            Path result = controller.getLatestStatusImagePath();

            assertThat(result).isNotNull();
            assertThat(result.toString()).endsWith("test_status.jpg");
        }

        @Test
        @DisplayName("returns null when metadata not loaded")
        void returnsNullWhenMetadataNotLoaded() {
            when(modelService.isMetadataLoaded()).thenReturn(false);

            Path result = controller.getLatestStatusImagePath();

            assertThat(result).isNull();
            verifyNoInteractions(printerStatusImageService);
        }

        @Test
        @DisplayName("returns null when status image returns null")
        void returnsNullWhenImageNull() {
            when(modelService.isMetadataLoaded()).thenReturn(true);
            when(printerStatusImageService.getStatusImage()).thenReturn(null);

            Path result = controller.getLatestStatusImagePath();

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("timelapse")
    class Timelapse {

        @Test
        @DisplayName("calls timelapseService.generate with id and returns OK")
        void generatesTimelapse() throws Exception {
            when(timelapseService.generate("abc123")).thenReturn(null);

            String result = controller.edit("abc123");

            assertThat(result).isEqualTo("OK");
            verify(timelapseService).generate("abc123");
        }
    }
}