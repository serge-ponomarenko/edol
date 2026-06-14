package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentUploadController {

    private static final Path MODELS_DIRECTORY = Path.of("models");

    private final CameraSnapshotStore cameraSnapshotStore;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PrinterConnectionConfigurationRepository printerConnectionConfigurationRepository;
    private final PrinterStateService printerStateService;
    private final LogContextFactory logContextFactory;

    @PostMapping(
            value = "/upload",
            consumes = "application/octet-stream"
    )
    public ResponseEntity<String> upload(
            @RequestParam String fileName,
            @RequestHeader("X-Agent-Id") String agentId,
            InputStream requestBody
    ) throws IOException {
        UUID printerId = printerConnectionConfigurationRepository
                .findByAgentId(agentId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No printer configured for agent: " + agentId
                        )
                )
                .getPrinter()
                .getId();
        Files.createDirectories(MODELS_DIRECTORY.resolve(printerId.toString()));

        Path targetFile = MODELS_DIRECTORY.resolve(printerId.toString()).resolve(fileName);

        Files.copy(
                requestBody,
                targetFile,
                StandardCopyOption.REPLACE_EXISTING
        );

        long size = Files.size(targetFile);

        applicationEventPublisher.publishEvent(
                new ModelAvailableEvent(printerId, targetFile)
        );

        PrinterState state = printerStateService.getState(printerId);

        logContextFactory
                .session(
                        log.atInfo(),
                        printerId,
                        state.getSessionId()
                )
                .log(
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

        UUID printerId = resolvePrinterId(agentId);

        cameraSnapshotStore.store(
                printerId,
                image
        );

        return ResponseEntity.ok("OK");
    }

    private UUID resolvePrinterId(String agentId) {
        return printerConnectionConfigurationRepository
                .findByAgentId(agentId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No printer configured for agent: " + agentId
                        )
                )
                .getPrinter()
                .getId();
    }
}