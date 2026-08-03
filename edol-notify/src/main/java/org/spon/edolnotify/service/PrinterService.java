package org.spon.edolnotify.service;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PrinterService {

    private final RestClient edolCoreClient;

    public PrinterState getState() {
        return edolCoreClient.get()
                .uri("/api/printers/state-default")
                .retrieve()
                .body(PrinterState.class);
    }

    public Path getLatestStatusImagePath() {
        return edolCoreClient.get()
                .uri("/camera/status-image")
                .retrieve()
                .body(Path.class);
    }

    public void sendStopCommand() {
        edolCoreClient.post()
                .uri("/api/request/stop")
                .retrieve()
                .toBodilessEntity();
    }

    public void sendResumeCommand() {
        edolCoreClient.post()
                .uri("/api/request/resume")
                .retrieve()
                .toBodilessEntity();
    }

    public void sendPauseCommand() {
        edolCoreClient.post()
                .uri("/api/request/pause")
                .retrieve()
                .toBodilessEntity();
    }

    public void sendFetchMetadataCommand() {
        edolCoreClient.post()
                .uri("/api/request/fetchmetadata")
                .retrieve()
                .toBodilessEntity();
    }

    public void sendPushAllCommand() {
        edolCoreClient.post()
                .uri("/api/request/pushall")
                .retrieve()
                .toBodilessEntity();
    }
}
