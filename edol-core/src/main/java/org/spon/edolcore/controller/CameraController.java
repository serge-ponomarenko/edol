package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.CameraSnapshot;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CameraController {

    private final CameraSnapshotStore store;
    private final PrinterStatusImageService printerStatusImageService;
    private final ModelMetadataWorkflowService modelMetadataWorkflowService;

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

    @GetMapping("/printers/{printerId}/camera/status-image")
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

    @GetMapping(value = "/printers/{printerId}/camera/status-image/content",
            produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> getStatusImageContent(@PathVariable UUID printerId) {
        Path image = getLatestStatusImagePath(printerId);
        if (image == null || !Files.isRegularFile(image) || !Files.isReadable(image)) {
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_JPEG).body(new FileSystemResource(image));
    }

}
