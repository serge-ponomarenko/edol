package org.spon.edolcore.service.printmetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.FtpsService;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelDownloadService {

    private final PrinterStateService printerStateService;
    private final FtpsService ftpsService;

    private static final Path MODELS_DIR = Path.of("models");

    public Path downloadCurrentModel() throws Exception {
        PrinterState state = printerStateService.getState();

        String fileName = state.getCurrentFile();

        if (fileName == null || fileName.isEmpty())
            throw new RuntimeException("No model currently loaded");

        Files.createDirectories(MODELS_DIR);

        Path localFile = MODELS_DIR.resolve(fileName);

        log.info("Downloading model: {}", fileName);

        ftpsService.download(fileName, localFile.toString());

        return localFile;
    }
}
