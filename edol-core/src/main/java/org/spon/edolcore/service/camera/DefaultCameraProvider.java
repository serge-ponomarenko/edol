package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.printer.PrinterService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultCameraProvider
        implements CameraProvider {

    private final PrinterService printerService;
    private final DirectCameraProvider directCameraProvider;
    private final AgentCameraProvider agentCameraProvider;

    @Override
    public byte[] capture(UUID printerId) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        return provider(printerId)
                .capture(printerId);
    }

    @Override
    public boolean supports(UUID printerId) {
        return provider(printerId)
                .supports(printerId);
    }

    private CameraProvider provider(
            UUID printerId
    ) {
        Printer printer =
                printerService.getPrinter(printerId);

        return switch (
                printer.getConnectionMode()
                ) {
            case DIRECT -> directCameraProvider;
            case AGENT -> agentCameraProvider;
        };
    }

}
