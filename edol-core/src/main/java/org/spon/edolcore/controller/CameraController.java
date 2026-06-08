package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.CameraSnapshot;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.timelapse.TimelapseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Path;

@RestController
@RequiredArgsConstructor
public class CameraController {

    private final CameraSnapshotStore store;
    private final TimelapseService timelapseService;
    private final PrinterStatusImageService printerStatusImageService;
    private final ModelMetadataWorkflowService modelMetadataWorkflowService;

    @GetMapping(value = "/camera/latest", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] latest() {
        CameraSnapshot snap = store.getLatest();

        if (snap == null)
            return new byte[0];

        return snap.getImage();
    }

    @GetMapping(value = "/camera/status-image")
    public Path getLatestStatusImagePath() {
        if (modelMetadataWorkflowService.isMetadataLoaded()) {
            File statusImage = printerStatusImageService.getStatusImage();
            if (statusImage != null) {
                return statusImage.toPath().toAbsolutePath();
            }
        }
        return null;
    }

    @GetMapping("/camera/timelapse/{id}")
    public String edit(@PathVariable String id) {
        timelapseService.generate(id);
        return "OK";
    }
}
