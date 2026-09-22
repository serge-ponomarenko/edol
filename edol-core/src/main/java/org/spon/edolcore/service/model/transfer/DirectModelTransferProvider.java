package org.spon.edolcore.service.model.transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.exception.ModelNotLoadedException;
import org.spon.edolcore.exception.ModelTransferException;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printer.ftps.CurlFtpsClient;
import org.spon.edolcore.service.printer.ftps.FtpsConnection;
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
    private final PrinterConnectionConfigurationRepository configurationRepository;
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

            PrinterConnectionConfiguration configuration =
                    configurationRepository
                            .findByPrinterId(printerId)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "Printer configuration not found: " + printerId
                                    )
                            );

            FtpsConnection ftpsConnection = new FtpsConnection(
                    configuration.getFtpHost(),
                    990,
                    "bblp",
                    configuration.getAccessCode()
            );

            curlFtpsClient.download(ftpsConnection, configuration.getModelDirectory() + "/" + fileName, localFile.toString());

            applicationEventPublisher.publishEvent(
                    new ModelAvailableEvent(printerId, localFile)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelTransferException(fileName, e);
        } catch (Exception e) {
            throw new ModelTransferException(fileName, e);
        }
    }
}
