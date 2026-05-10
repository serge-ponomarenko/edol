package org.spon.edolcore.service.camera;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CameraBackgroundService {

    private final CameraService cameraService;
    private final CameraSnapshotStore store;

    public CameraBackgroundService(CameraService cameraService,
                                   CameraSnapshotStore store) {
        this.cameraService = cameraService;
        this.store = store;
    }

    @PostConstruct
    public void init() {
        log.info("Camera background reader started");
    }

    @Scheduled(fixedDelay = 15000)
    public void capture() {
        try {
            byte[] image = cameraService.captureImage();

            if (image != null && image.length > 0) {
                store.store(image);
            }

        } catch (Exception e) {
            log.error("Camera capture failed: {}", e.getMessage());
        }
    }
}
