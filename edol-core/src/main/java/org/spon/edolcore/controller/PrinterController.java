package org.spon.edolcore.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printermqtt.BambuMqttClient;
import org.spon.edolcore.service.printmetadata.ModelService;
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

    private final PrinterStateService service;
    private final BambuMqttClient client;
    private final ModelService modelService;

    @GetMapping("/state")
    public PrinterState getState() {
        if (client.isConnected()) {
            return service.getState();
        } else {
            PrinterState printerState = new PrinterState();
            printerState.setOnline(false);
            return printerState;
        }
    }

    @GetMapping("/connection")
    public boolean isConnected() {
        return client.isConnected();
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
    public ResponseEntity<?> skipObjects(@RequestBody SkipObjectsRequest request) {
        client.skipObjects(request.getObjectIds());

        return ResponseEntity.ok().body(Map.of(
                "status", "ok",
                "skipped", request.getObjectIds()
        ));
    }

    @PostMapping("/request/spool-change")
    public ResponseEntity<?> skipObjects(@RequestBody BambuMqttClient.SpoolChangeRequest request) {
        client.spoolChange(request);
        log.info("-> Spool change API request");

        return ResponseEntity.ok().body(Map.of(
                "status", "ok"
        ));
    }

    @PostMapping("/request/pause")
    public ResponseEntity<?> pausePrint() {
        client.pause();
        log.info("-> Print PAUSE API request");

        return ResponseEntity.ok().body(Map.of(
                "status", "ok"
        ));
    }

    @PostMapping("/request/resume")
    public ResponseEntity<?> resumePrint() {
        client.resume();
        log.info("-> Print RESUME API request");

        return ResponseEntity.ok().body(Map.of(
                "status", "ok"
        ));
    }

    @PostMapping("/request/stop")
    public ResponseEntity<?> stopPrint() {
        client.stop();
        log.info("-> Print STOP API request");

        return ResponseEntity.ok().body(Map.of(
                "status", "ok"
        ));
    }

    @PostMapping("/request/pushall")
    public ResponseEntity<?> pushAll() {
        client.pushAll();
        log.info("-> Print PushAll API request");

        return ResponseEntity.ok().body(Map.of(
                "status", "ok"
        ));
    }

    @PostMapping("/request/fetchmetadata")
    public ResponseEntity<?> fetchMetadata() throws Exception {
        modelService.fetchMetadata();

        return ResponseEntity.ok().body(Map.of(
                "status", "ok"
        ));
    }

    private @NonNull ResponseEntity<Resource> getImageResponseEntity(String imageName) {
        Path platePath = Path.of(
                "models",
                "metadata",
                imageName + "_" + service.getState().getPlateIndex() + ".png"
        );

        if (!Files.exists(platePath) || !modelService.isMetadataLoaded()) {
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