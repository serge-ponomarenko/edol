package org.spon.edolcore.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.model.dto.SpoolChangeRequestDto;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PrinterCommandController {

    private final LogContextFactory logContextFactory;

    private final PrinterStateService printerStateService;
    private final PrinterCommandGateway printerCommandGateway;
    private final DefaultPrinterResolver defaultPrinterResolver;
    private final ModelMetadataWorkflowService modelMetadataWorkflowService;

    private static final ResponseEntity<Map<String, Object>> okResponseEntity =
            ResponseEntity.ok().body(Map.of(
                    "status", "ok"
            ));

    @Deprecated(forRemoval = true)
    @PostMapping("/request/skip-objects")
    public ResponseEntity<Map<String, Object>> skipObjects(@RequestBody SkipObjectsRequest request) {
        return skipObjects(
                defaultPrinterResolver.resolve(),
                request
        );
    }

    @PostMapping("/printers/{printerId}/commands/skip-objects")
    public ResponseEntity<Map<String, Object>> skipObjects(
            @PathVariable UUID printerId,
            @RequestBody SkipObjectsRequest request
    ) {
        List<Integer> objectIds = request.getObjectIds();

        printerCommandGateway.skipObjects(
                printerId,
                objectIds
        );

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Skip objects API request"
                );

        printerStateService.getState(printerId).getPrintObjects().forEach(po -> {
            if (objectIds.contains(po.getId())) {
                po.setSkipped(true);
            }
        });

        return okResponseEntity;
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/request/spool-change")
    public ResponseEntity<Map<String, Object>> spoolChange(@RequestBody SpoolChangeRequestDto request) {
        return spoolChange(
                defaultPrinterResolver.resolve(),
                request
        );
    }

    @PostMapping("/printers/{printerId}/commands/spool-change")
    public ResponseEntity<Map<String, Object>> spoolChange(
            @PathVariable UUID printerId,
            @RequestBody SpoolChangeRequestDto request
    ) {
        printerCommandGateway.spoolChange(
                printerId,
                request
        );

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Spool change API request"
                );

        return okResponseEntity;
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/request/pause")
    public ResponseEntity<Map<String, Object>> pause() {
        return pause(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/printers/{printerId}/commands/pause")
    public ResponseEntity<Map<String, Object>> pause(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.pause(printerId);

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Print PAUSE API request"
                );

        return okResponseEntity;
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/request/resume")
    public ResponseEntity<Map<String, Object>> resume() {
        return resume(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/printers/{printerId}/commands/resume")
    public ResponseEntity<Map<String, Object>> resume(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.resume(printerId);

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Print RESUME API request"
                );

        return okResponseEntity;
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/request/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        return stop(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/printers/{printerId}/commands/stop")
    public ResponseEntity<Map<String, Object>> stop(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.stop(printerId);

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Print STOP API request"
                );

        return okResponseEntity;
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/request/pushall")
    public ResponseEntity<Map<String, Object>> pushAll() {
        return pushAll(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/printers/{printerId}/commands/pushall")
    public ResponseEntity<Map<String, Object>> pushAll(
            @PathVariable UUID printerId
    ) {
        printerCommandGateway.pushAll(printerId);

        logContextFactory
                .printer(
                        log.atInfo(),
                        printerId
                )
                .log(
                        "Print PushAll API request"
                );

        return okResponseEntity;
    }

    @Deprecated(forRemoval = true)
    @PostMapping("/request/fetchmetadata")
    public ResponseEntity<Map<String, Object>> requestMetadata() {
        return requestMetadata(
                defaultPrinterResolver.resolve()
        );
    }

    @PostMapping("/printers/{printerId}/commands/fetchmetadata")
    public ResponseEntity<Map<String, Object>> requestMetadata(
            @PathVariable UUID printerId
    ) {
        modelMetadataWorkflowService.requestMetadata(printerId);
        return okResponseEntity;
    }

    @Data
    public static class SkipObjectsRequest {
        private List<Integer> objectIds;
    }

}
