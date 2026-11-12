package org.spon.edolcore.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.nio.file.Files;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@ExtendWith(MockitoExtension.class)
class CameraControllerTest {

    @Test
    void servesExactlyTheExistingRenderedImageWithoutChangingItsOverlay(@TempDir Path directory) throws Exception {
        byte[] rendered = {(byte) 0xff, (byte) 0xd8, 11, 22, (byte) 0xff, (byte) 0xd9};
        Path image = Files.write(directory.resolve("original-status-image.jpg"), rendered);
        when(modelMetadataWorkflowService.isMetadataLoaded(PRINTER_ID)).thenReturn(true);
        when(printerStatusImageService.getStatusImage(PRINTER_ID)).thenReturn(image.toFile());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/printers/{printerId}/camera/status-image/content", PRINTER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(rendered));

        verify(printerStatusImageService).getStatusImage(PRINTER_ID);
        verifyNoInteractions(cameraSnapshotStore);
    }

    @Test
    void unavailableStatusImageReturnsNoContentWithoutSubstitutingARawSnapshot() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/printers/{printerId}/camera/status-image/content", PRINTER_ID))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        verifyNoInteractions(cameraSnapshotStore, printerStatusImageService);
    }

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
