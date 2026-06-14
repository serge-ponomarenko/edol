package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.PrinterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CameraBackgroundService {

    private final CameraSnapshotStore store;
    private final DefaultCameraProvider cameraProvider;
    private final PrinterService printerService;
    private final LogContextFactory logContextFactory;

    @Scheduled(fixedDelay = 15000)
    public void capture() {
        for (Printer printer :
                printerService.getEnabledPrinters()) {

            UUID printerId =
                    printer.getId();

            try {

                if (!cameraProvider.supports(
                        printerId
                )) {
                    continue;
                }

                byte[] image =
                        cameraProvider.capture(
                                printerId
                        );

                if (image != null
                        && image.length > 0) {

                    store.store(
                            printerId,
                            image
                    );
                }

            } catch (Exception e) {
                logContextFactory
                        .printer(
                                log.atError(),
                                printerId
                        )
                        .log(
                                "Camera capture failed ",
                                e
                        );
            }
        }
    }
}
