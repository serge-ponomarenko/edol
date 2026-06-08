package org.spon.edolcore.service.model.metadata;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.BoundingBox;
import org.spon.edol.model.Filament;
import org.spon.edol.model.PrintObject;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.transfer.ModelTransferProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelMetadataWorkflowService {

    private final ModelTransferProvider modelTransferProvider;
    private final ModelMetadataService metadataService;
    private final SliceInfoParserService sliceInfoParserService;
    private final PrinterStateService printerStateService;
    private final ProjectSettingsParserService projectSettingsParserService;
    private final PlateParserService plateParserService;

    @Setter
    @Getter
    private boolean metadataLoaded = false;

    public void requestMetadata() {
        modelTransferProvider.requestModel();
    }

    public void parseMetadata(Path model) throws Exception {
        Path output = Path.of("models/metadata");

        log.info("Extracting metadata from {}", model);
        Path path = metadataService.extractMetadata(model, output);
        log.info("Extracted metadata from {}", path);

        log.info("Starting metadata parsing");
        Path sliceInfoPath = path.resolve("slice_info.config");
        List<Filament> filaments =
                sliceInfoParserService.parseFilaments(sliceInfoPath);

        List<PrintObject> printObjects = sliceInfoParserService.parsePrintObjects(sliceInfoPath);

        int plateIndex = sliceInfoParserService.extractPlateIndex(sliceInfoPath);
        printerStateService.getState().setPlateIndex(plateIndex);

        metadataService.extractModelImage(model, output, plateIndex);

        projectSettingsParserService.enrichFilaments(
                path.resolve("project_settings.config"),
                filaments,
                printerStateService.getState().getAmsMapping()
        );

        printerStateService.getState().setFilaments(filaments);

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

        printerStateService.getState().setPrintObjects(printObjects);

        log.info("Metadata has been parsed successfully");

        metadataLoaded = true;
    }
}
