package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeCatalogEnumerator;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "edol-core.runtime.printer-runtime-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CameraBackgroundService {

    private final CameraSnapshotStore store;
    private final DefaultCameraProvider cameraProvider;
    private final CoreRuntimeCatalogEnumerator runtimeCatalogEnumerator;
    private final CoreRuntimeTenantExecutor runtimeTenantExecutor;
    private final LogContextFactory logContextFactory;
    private final ExecutorService virtualThreadExecutor;

    @Scheduled(fixedDelay = 15000)
    public void capture() {
        for (var entry : runtimeCatalogEnumerator.enabledPrinters()) {
            virtualThreadExecutor.submit(() ->
                    runtimeTenantExecutor.execute(entry, () -> capture(entry.printerId()))
            );
        }
    }

    private void capture(UUID printerId) {
        if (!cameraProvider.supports(printerId)) {
            return;
        }

        try {
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
