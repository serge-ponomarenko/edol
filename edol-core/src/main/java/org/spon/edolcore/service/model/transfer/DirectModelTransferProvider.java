package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.exception.ModelNotLoadedException;
import org.spon.edolcore.exception.ModelTransferException;
import org.spon.edolcore.service.FtpsService;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(
        value = "edol.printer.connection-mode",
        havingValue = "DIRECT",
        matchIfMissing = true
)
@Slf4j
@RequiredArgsConstructor
public class DirectModelTransferProvider implements ModelTransferProvider {

    private final PrinterStateService printerStateService;
    private final FtpsService ftpsService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void requestModel() {
        PrinterState state = printerStateService.getState();

        String fileName = state.getCurrentFile();

        if (fileName == null || fileName.isEmpty())
            throw new ModelNotLoadedException();

        Path localFile = MODELS_DIR.resolve(fileName);

        try {
            Files.createDirectories(MODELS_DIR);

            log.info("Downloading model: {}", fileName);

            Files.deleteIfExists(localFile);

            ftpsService.download(fileName, localFile.toString());

            applicationEventPublisher.publishEvent(
                    new ModelAvailableEvent(localFile)
            );
        } catch (Exception e) {
            throw new ModelTransferException(fileName, e);
        }
    }
}
