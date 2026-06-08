package org.spon.edolcore.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.model.dto.SpoolChangeRequestDto;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/printer")
@RequiredArgsConstructor
@Slf4j
public class PrinterController {

    ResponseEntity<Map<String, Object>> okResponseEntity =
            ResponseEntity.ok().body(Map.of(
                    "status", "ok"
            ));

    private final PrinterStateService printerStateService;
    private final PrinterConnectivityProvider connectivityProvider;
    private final ModelMetadataWorkflowService modelMetadataWorkflowService;
    private final PrinterCommandGateway printerCommandGateway;

    @GetMapping("/state")
    public PrinterState getState() {
        if (connectivityProvider.isConnected()) {
            return printerStateService.getState();
        } else {
            PrinterState printerState = new PrinterState();
            printerState.setOnline(false);
            return printerState;
        }
    }

    @GetMapping("/connection")
    public boolean isConnected() {
        return connectivityProvider.isConnected();
    }

    @GetMapping(value = "/modelimage", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getModelImage() {
        return getImageResponseEntity("plate");
    }

    @GetMapping(value = "/modeltopimage", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getModelTopImage() {
        return getImageResponseEntity("top");
    }

    @PostMapping("/request/skip-objects")
    public ResponseEntity<Map<String, Object>> skipObjects(@RequestBody SkipObjectsRequest request) {
        List<Integer> objectIds = request.getObjectIds();
        printerCommandGateway.skipObjects(objectIds);

        printerStateService.getState().getPrintObjects().forEach(po -> {
            if (objectIds.contains(po.getId())) {
                po.setSkipped(true);
            }
        });


        return okResponseEntity;
    }

    @PostMapping("/request/spool-change")
    public ResponseEntity<Map<String, Object>> spoolChange(@RequestBody SpoolChangeRequestDto request) {
        printerCommandGateway.spoolChange(request);
        log.info("-> Spool change API request");

        return okResponseEntity;
    }

    @PostMapping("/request/pause")
    public ResponseEntity<Map<String, Object>> pausePrint() {
        printerCommandGateway.pause();
        log.info("-> Print PAUSE API request");

        return okResponseEntity;
    }

    @PostMapping("/request/resume")
    public ResponseEntity<Map<String, Object>> resumePrint() {
        printerCommandGateway.resume();
        log.info("-> Print RESUME API request");

        return okResponseEntity;
    }

    @PostMapping("/request/stop")
    public ResponseEntity<Map<String, Object>> stopPrint() {
        printerCommandGateway.stop();
        log.info("-> Print STOP API request");

        return okResponseEntity;
    }

    @PostMapping("/request/pushall")
    public ResponseEntity<Map<String, Object>> pushAll() {
        printerCommandGateway.pushAll();
        log.info("-> Print PushAll API request");

        return okResponseEntity;
    }

    @PostMapping("/request/fetchmetadata")
    public ResponseEntity<Map<String, Object>> fetchMetadata() {
        modelMetadataWorkflowService.requestMetadata();

        return okResponseEntity;
    }

    private @NonNull ResponseEntity<Resource> getImageResponseEntity(String imageName) {
        Path platePath = Path.of(
                "models",
                "metadata",
                imageName + "_" + printerStateService.getState().getPlateIndex() + ".png"
        );

        if (!Files.exists(platePath) || !modelMetadataWorkflowService.isMetadataLoaded()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(platePath);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(resource);
    }

    @Data
    public static class SkipObjectsRequest {
        private List<Integer> objectIds;
    }

}
