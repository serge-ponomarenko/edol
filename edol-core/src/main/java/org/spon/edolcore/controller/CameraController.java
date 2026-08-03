package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.CameraSnapshot;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CameraController {

    private final CameraSnapshotStore store;
    private final PrinterStatusImageService printerStatusImageService;
    private final ModelMetadataWorkflowService modelMetadataWorkflowService;
    private final DefaultPrinterResolver defaultPrinterResolver;

    @Deprecated(forRemoval = true)
    @GetMapping(
            value = "/camera/latest",
            produces = MediaType.IMAGE_JPEG_VALUE
    )
    public byte[] latest() {
        return latest(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping(
            value = "/printers/{printerId}/camera/snapshot",
            produces = MediaType.IMAGE_JPEG_VALUE
    )
    public byte[] latest(
            @PathVariable UUID printerId
    ) {
        CameraSnapshot snap =
                store.getLatest(printerId);

        if (snap == null) {
            return new byte[0];
        }

        return snap.getImage();
    }

    @Deprecated(forRemoval = true)
    @GetMapping("/camera/status-image")
    public Path getLatestStatusImagePath() {
        return getLatestStatusImagePath(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping("/api/printers/{printerId}/camera/status-image")
    public Path getLatestStatusImagePath(
            @PathVariable UUID printerId
    ) {
        if (modelMetadataWorkflowService.isMetadataLoaded(printerId)) {
            File statusImage =
                    printerStatusImageService.getStatusImage(printerId);

            if (statusImage != null) {
                return statusImage.toPath().toAbsolutePath();
            }
        }

        return null;
    }

}
