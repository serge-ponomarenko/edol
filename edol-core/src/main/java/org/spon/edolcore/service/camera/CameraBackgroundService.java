package org.spon.edolcore.service.camera;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CameraBackgroundService {

    private final CameraProvider cameraProvider;
    private final CameraSnapshotStore store;

    @Value("${edol.printer.connection-mode}")
    private String connectionMode;

    @PostConstruct
    public void init() {
        if ("AGENT".equalsIgnoreCase(connectionMode)) {
            log.info("Camera background capture disabled in AGENT mode");
        } else {
            log.info("Camera background reader started");
        }
    }

    @Scheduled(fixedDelay = 15000)
    public void capture() {
        if ("AGENT".equalsIgnoreCase(connectionMode)) {
            return;
        }

        try {
            byte[] image = cameraProvider.capture();

            if (image != null && image.length > 0) {
                store.store(image);
            }

        } catch (Exception e) {
            log.error("Camera capture failed: {}", e.getMessage());
        }
    }
}
