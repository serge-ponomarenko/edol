package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.upload.ModelFileStorage;
import org.spon.edolcore.service.upload.SnapshotValidator;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentUploadController {

    private final CameraSnapshotStore cameraSnapshotStore;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PrinterConnectionConfigurationRepository printerConnectionConfigurationRepository;
    private final ModelFileStorage modelFileStorage;
    private final SnapshotValidator snapshotValidator;

    @PostMapping(
            value = "/upload",
            consumes = "application/octet-stream"
    )
    public ResponseEntity<String> upload(
            @RequestParam String fileName,
            @RequestHeader("X-Agent-Id") String agentId,
            InputStream requestBody
    ) throws IOException {
        try {
            UUID printerId = resolvePrinterId(agentId);
            var stored = modelFileStorage.store(printerId, fileName, requestBody);
            applicationEventPublisher.publishEvent(new ModelAvailableEvent(printerId, stored.path()));
            log.info("Agent model upload accepted: printerId={}, bytes={}", printerId, stored.bytes());
            return ResponseEntity.ok("OK");
        } catch (IOException | ResponseStatusException exception) {
            log.warn("Agent upload rejected: kind=model, status={}", rejectionStatus(exception));
            throw exception;
        }
    }

    @PostMapping(
            value = "/camera/snapshot",
            consumes = "image/jpeg"
    )
    public ResponseEntity<String> uploadSnapshot(
            @RequestHeader("X-Agent-Id") String agentId,
            InputStream requestBody
    ) throws IOException {
        try {
            UUID printerId = resolvePrinterId(agentId);
            byte[] image = snapshotValidator.read(requestBody);
            cameraSnapshotStore.store(printerId, image);
            log.debug("Agent snapshot accepted: printerId={}, bytes={}", printerId, image.length);
            return ResponseEntity.ok("OK");
        } catch (IOException | ResponseStatusException exception) {
            log.warn("Agent upload rejected: kind=snapshot, status={}", rejectionStatus(exception));
            throw exception;
        }
    }

    private UUID resolvePrinterId(String agentId) {
        return printerConnectionConfigurationRepository
                .findByAgentId(agentId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent printer is unavailable")
                )
                .getPrinter()
                .getId();
    }

    private int rejectionStatus(Exception exception) {
        return exception instanceof ResponseStatusException rejected ? rejected.getStatusCode().value() : 500;
    }
}
