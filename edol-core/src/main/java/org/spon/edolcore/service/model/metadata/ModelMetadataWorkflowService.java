package org.spon.edolcore.service.model.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.BoundingBox;
import org.spon.edol.model.Filament;
import org.spon.edol.model.PrintObject;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.transfer.DefaultModelTransferProvider;
import org.spon.edolcore.service.printer.runtime.MetadataRuntimeState;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeContextProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelMetadataWorkflowService {

    private final DefaultModelTransferProvider modelTransferProvider;
    private final ModelMetadataService metadataService;
    private final SliceInfoParserService sliceInfoParserService;
    private final PrinterStateService printerStateService;
    private final ProjectSettingsParserService projectSettingsParserService;
    private final PlateParserService plateParserService;
    private final PrinterRuntimeContextProvider runtimeContextProvider;

    public void requestMetadata(UUID printerId) {
        modelTransferProvider.requestModel(printerId);
    }

    public void parseMetadata(UUID printerId, Path model) throws Exception {
        Path output = Path.of("models", printerId.toString(), "metadata");

        log.info("Extracting metadata from {}", model);
        Path path = metadataService.extractMetadata(model, output);
        log.info("Extracted metadata from {}", path);

        log.info("Starting metadata parsing");
        Path sliceInfoPath = path.resolve("slice_info.config");
        List<Filament> filaments =
                sliceInfoParserService.parseFilaments(sliceInfoPath);

        List<PrintObject> printObjects = sliceInfoParserService.parsePrintObjects(sliceInfoPath);

        int plateIndex = sliceInfoParserService.extractPlateIndex(sliceInfoPath);
        printerStateService.getState(printerId).setPlateIndex(plateIndex);

        metadataService.extractModelImage(model, output, plateIndex);

        projectSettingsParserService.enrichFilaments(
                path.resolve("project_settings.config"),
                filaments,
                printerStateService.getState(printerId).getAmsMapping()
        );

        printerStateService.getState(printerId).setFilaments(filaments);

        Path plateJsonPath = path.resolve("plate_" + plateIndex + ".json");
        List<BoundingBox> boxes = plateParserService.parse(plateJsonPath);

        if (printObjects.size() != boxes.size()) {
            log.warn("Mismatch between printer and plate objects!");
        } else {
            for (int i = 0; i < printObjects.size(); i++) {
                PrintObject printerObj = printObjects.get(i);
                printerObj.setBoundingBox(boxes.get(i));
            }
        }

        printerStateService.getState(printerId).setPrintObjects(printObjects);

        log.info("Metadata has been parsed successfully");

        runtime(printerId).setMetadataLoaded(true);
    }

    public boolean isMetadataLoaded(UUID printerId) {
        return runtime(printerId).isMetadataLoaded();
    }

    public void setMetadataLoaded(UUID printerId, boolean metadataLoaded) {
        runtime(printerId).setMetadataLoaded(metadataLoaded);
    }

    private MetadataRuntimeState runtime(UUID printerId) {
        return runtimeContextProvider
                .getContext(printerId)
                .getMetadataRuntimeState();
    }
}
