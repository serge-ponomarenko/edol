package org.spon.edolcore.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;
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
    private final DefaultPrinterResolver defaultPrinterResolver;


    @GetMapping("/state")
    public PrinterState getState() {
        return getState(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping("/api/printers/{printerId}/state")
    public PrinterState getState(
            @PathVariable UUID printerId
    ) {
        if (connectivityProvider.isConnected(printerId)) {
            return printerStateService.getState(printerId);
        }

        PrinterState printerState = new PrinterState();
        printerState.setOnline(false);

        return printerState;
    }

    @GetMapping("/connection")
    public boolean isConnected() {
        return isConnected(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping("/api/printers/{printerId}/connection")
    public boolean isConnected(
            @PathVariable UUID printerId
    ) {
        return connectivityProvider.isConnected(printerId);
    }

    @GetMapping(value = "/modelimage", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getModelImage() {
        return getModelImage(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping(
            value = "/api/printers/{printerId}/modelimage",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<Resource> getModelImage(
            @PathVariable UUID printerId
    ) {
        return getImageResponseEntity(
                printerId,
                "plate"
        );
    }

    @GetMapping(value = "/modeltopimage", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getModelTopImage() {
        return getModelImage(
                defaultPrinterResolver.resolve()
        );
    }

    @GetMapping(
            value = "/api/printers/{printerId}/modeltopimage",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<Resource> getModelTopImage(
            @PathVariable UUID printerId
    ) {
        return getImageResponseEntity(
                printerId,
                "top"
        );
    }

    @PostMapping("/request/skip-objects")
    public ResponseEntity<Map<String, Object>> skipObjects(@RequestBody SkipObjectsRequest request) {
        return skipObjects(
                defaultPrinterResolver.resolve(),
                request
        );
    }

    @PostMapping("/api/printers/{printerId}/request/skip-objects")
    public ResponseEntity<Map<String, Object>> skipObjects(
            @PathVariable UUID printerId,
            @RequestBody SkipObjectsRequest request
    ) {
        List<Integer> objectIds = request.getObjectIds();

        printerCommandGateway.skipObjects(
                printerId,
                objectIds
        );

        printerStateService.getState(printerId).getPrintObjects().forEach(po -> {
            if (objectIds.contains(po.getId())) {
                po.setSkipped(true);
            }
        });

        return okResponseEntity;
    }

    @PostMapping("/request/spool-change")
    public ResponseEntity<Map<String, Object>> spoolChange(@RequestBody SpoolChangeRequestDto request) {
        return spoolChange(
                defaultPrinterResolver.resolve(),
                request
        );
    }

    @PostMapping("/api/printers/{printerId}/request/spool-change")
    public ResponseEntity<Map<String, Object>> spoolChange(
            @PathVariable UUID printerId,
            @RequestBody SpoolChangeRequestDto request
    ) {
        printerCommandGateway.spoolChange(
                printerId,
                request
        );

        log.info(
                "-> Spool change API request [{}]",
                printerId
        );

        return okResponseEntity;
    }

    @PostMapping("/request/pause")
    public ResponseEntity<Map<String, Object>> pausePrint() {
        return pausePrint(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/api/printers/{printerId}/request/pause")
    public ResponseEntity<Map<String, Object>> pausePrint(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.pause(printerId);

        log.info(
                "-> Print PAUSE API request [{}]",
                printerId
        );

        return okResponseEntity;
    }

    @PostMapping("/request/resume")
    public ResponseEntity<Map<String, Object>> resumePrint() {
        return resumePrint(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/api/printers/{printerId}/request/resume")
    public ResponseEntity<Map<String, Object>> resumePrint(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.resume(printerId);

        log.info(
                "-> Print RESUME API request [{}]",
                printerId
        );

        return okResponseEntity;
    }

    @PostMapping("/request/stop")
    public ResponseEntity<Map<String, Object>> stopPrint() {
        return stopPrint(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/api/printers/{printerId}/request/stop")
    public ResponseEntity<Map<String, Object>> stopPrint(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.stop(printerId);

        log.info(
                "-> Print STOP API request [{}]",
                printerId
        );

        return okResponseEntity;
    }

    @PostMapping("/request/pushall")
    public ResponseEntity<Map<String, Object>> pushAll() {
        return pushAll(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/api/printers/{printerId}/request/pushall")
    public ResponseEntity<Map<String, Object>> pushAll(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.pushAll(printerId);

        log.info(
                "-> Print PushAll API request [{}]",
                printerId
        );

        return okResponseEntity;
    }

    @PostMapping("/request/fetchmetadata")
    public ResponseEntity<Map<String, Object>> fetchMetadata() {
        return fetchMetadata(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/api/printers/{printerId}/request/fetchmetadata")
    public ResponseEntity<Map<String, Object>> fetchMetadata(
            @PathVariable UUID printerId
    ) {
        modelMetadataWorkflowService.requestMetadata(printerId);
        return okResponseEntity;
    }


    private ResponseEntity<Resource> getImageResponseEntity(
            UUID printerId,
            String imageName
    ) {
        Path platePath = Path.of(
                "models",
                printerId.toString(),
                "metadata",
                imageName + "_" + printerStateService
                        .getState(printerId)
                        .getPlateIndex() + ".png"
        );

        if (!Files.exists(platePath) || !modelMetadataWorkflowService.isMetadataLoaded(printerId)) {
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
