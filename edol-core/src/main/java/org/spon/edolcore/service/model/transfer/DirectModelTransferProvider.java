package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.exception.ModelNotLoadedException;
import org.spon.edolcore.exception.ModelTransferException;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.ftps.CurlFtpsClient;
import org.spon.edolcore.service.printer.ftps.FtpsConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DirectModelTransferProvider implements ModelTransferProvider {

    private final PrinterStateService printerStateService;
    private final CurlFtpsClient curlFtpsClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void requestModel(UUID printerId) {
        PrinterState state = printerStateService.getState(printerId);

        String fileName = state.getCurrentFile();

        if (fileName == null || fileName.isEmpty())
            throw new ModelNotLoadedException();

        Path localFile = MODELS_DIR.resolve(printerId.toString()).resolve(fileName);

        try {
            Files.createDirectories(MODELS_DIR.resolve(printerId.toString()));

            Files.deleteIfExists(localFile);

            FtpsConnection ftpsConnection = new FtpsConnection(
                    ip,
                    990,
                    "bblp",
                    accessCode
            );

            curlFtpsClient.download(ftpsConnection, modelDirectory + "/" + fileName, localFile.toString());

            applicationEventPublisher.publishEvent(
                    new ModelAvailableEvent(printerId, localFile)
            );
        } catch (Exception e) {
            throw new ModelTransferException(fileName, e);
        }
    }
}
