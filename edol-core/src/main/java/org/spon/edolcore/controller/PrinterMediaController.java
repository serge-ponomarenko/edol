package org.spon.edolcore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PrinterMediaController {

    private final PrinterStateService printerStateService;
    private final ModelMetadataWorkflowService modelMetadataWorkflowService;

    @GetMapping(
            value = "/printers/{printerId}/media/model/plate",
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

    @GetMapping(
            value = "/printers/{printerId}/media/model/top",
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
}
