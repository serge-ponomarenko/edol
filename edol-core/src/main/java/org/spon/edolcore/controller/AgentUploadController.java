package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentUploadController {

    private static final Path MODELS_DIRECTORY = Path.of("models");

    private final CameraSnapshotStore cameraSnapshotStore;
    private final ApplicationEventPublisher applicationEventPublisher;

    @PostMapping(
            value = "/upload",
            consumes = "application/octet-stream"
    )
    public ResponseEntity<String> upload(
            @RequestParam String fileName,
            InputStream requestBody
    ) throws IOException {
        Files.createDirectories(MODELS_DIRECTORY);

        Path targetFile = MODELS_DIRECTORY.resolve(fileName);

        Files.copy(
                requestBody,
                targetFile,
                StandardCopyOption.REPLACE_EXISTING
        );

        long size = Files.size(targetFile);

        applicationEventPublisher.publishEvent(
                new ModelAvailableEvent(targetFile)
        );

        log.info(
                "File uploaded: {} ({} bytes)",
                fileName,
                size
        );

        return ResponseEntity.ok("OK");
    }

    @PostMapping(
            value = "/camera/snapshot",
            consumes = "image/jpeg"
    )
    public ResponseEntity<String> uploadSnapshot(
            @RequestHeader("X-Agent-Id") String agentId,
            InputStream requestBody
    ) throws IOException {
        byte[] image = requestBody.readAllBytes();

        cameraSnapshotStore.store(image);

        log.debug(
                "Camera snapshot uploaded: {} ({} bytes)",
                agentId,
                image.length
        );

        return ResponseEntity.ok("OK");
    }
}