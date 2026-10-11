package org.spon.edolcore.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CameraControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private CameraSnapshotStore cameraSnapshotStore;

    @Mock
    private PrinterStatusImageService printerStatusImageService;

    @Mock
    private ModelMetadataWorkflowService modelMetadataWorkflowService;

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

    @Test
    void returnsNotFoundWhenThePrinterRuntimeIsUnavailable() throws Exception {
        when(cameraSnapshotStore.getLatest(PRINTER_ID))
                .thenThrow(new PrinterRuntimeNotFoundException(PRINTER_ID));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/printers/{printerId}/camera/snapshot", PRINTER_ID))
                .andExpect(status().isNotFound());
    }
}
