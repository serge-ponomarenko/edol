package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterCameraProvider;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultCameraProvider
        implements CameraProvider {

    private final PrinterManagementService printerManagementService;
    private final LegacyCameraProvider legacyCameraProvider;
    private final RtspsCameraProvider rtspsCameraProvider;
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
                printerManagementService.getPrinter(printerId);

        return switch (
                printer.getConnectionMode()
                ) {
            case DIRECT -> {
                if (printer.getCameraProvider() == PrinterCameraProvider.LEGACY) {
                    yield legacyCameraProvider;
                } else {
                    yield rtspsCameraProvider;
                }
            }
            case AGENT -> agentCameraProvider;
        };
    }

}
