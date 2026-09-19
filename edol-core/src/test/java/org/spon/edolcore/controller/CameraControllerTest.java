package org.spon.edolcore.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CameraControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private CameraSnapshotStore cameraSnapshotStore;

    @Mock
    private PrinterStatusImageService printerStatusImageService;

    @Mock
    private ModelMetadataWorkflowService modelMetadataWorkflowService;

    @Mock
    private DefaultPrinterResolver defaultPrinterResolver;

    @InjectMocks
    private CameraController controller;

    @Test
    void exposesPrinterScopedStatusImageWithoutDuplicateApiPrefix() throws NoSuchMethodException {
        Path expected = Path.of("snapshots", "printer-status.jpg").toAbsolutePath();
        when(modelMetadataWorkflowService.isMetadataLoaded(PRINTER_ID)).thenReturn(true);
        when(printerStatusImageService.getStatusImage(PRINTER_ID)).thenReturn(expected.toFile());

        Path result = controller.getLatestStatusImagePath(PRINTER_ID);
        GetMapping mapping = CameraController.class
                .getMethod("getLatestStatusImagePath", UUID.class)
                .getAnnotation(GetMapping.class);

        assertThat(result).isEqualTo(expected);
        assertThat(mapping.value()).containsExactly("/printers/{printerId}/camera/status-image");
    }
}
