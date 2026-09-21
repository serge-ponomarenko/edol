package org.spon.edolnotify.service;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.model.PrinterSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrinterService {

    private final RestClient edolCoreClient;

    public List<PrinterSummary> getPrinters() {
        PrinterSummary[] printers = edolCoreClient.get()
                .uri("/api/printers")
                .retrieve()
                .body(PrinterSummary[].class);
        return printers == null ? List.of() : Arrays.asList(printers);
    }

    public PrinterState getState(UUID printerId) {
        return edolCoreClient.get()
                .uri("/api/printers/{printerId}/state", printerId)
                .retrieve()
                .body(PrinterState.class);
    }

    public List<PrinterState> getStates() {
        PrinterState[] states = edolCoreClient.get()
                .uri("/api/printers/state")
                .retrieve()
                .body(PrinterState[].class);
        return states == null ? List.of() : Arrays.asList(states);
    }

    public Path getLatestStatusImagePath(UUID printerId) {
        return edolCoreClient.get()
                .uri("/api/printers/{printerId}/camera/status-image", printerId)
                .retrieve()
                .body(Path.class);
    }

    public void sendStopCommand(UUID printerId) {
        edolCoreClient.post()
                .uri("/api/printers/{printerId}/commands/stop", printerId)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendResumeCommand(UUID printerId) {
        edolCoreClient.post()
                .uri("/api/printers/{printerId}/commands/resume", printerId)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendPauseCommand(UUID printerId) {
        edolCoreClient.post()
                .uri("/api/printers/{printerId}/commands/pause", printerId)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendFetchMetadataCommand(UUID printerId) {
        edolCoreClient.post()
                .uri("/api/printers/{printerId}/commands/fetchmetadata", printerId)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendPushAllCommand(UUID printerId) {
        edolCoreClient.post()
                .uri("/api/printers/{printerId}/commands/pushall", printerId)
                .retrieve()
                .toBodilessEntity();
    }
}
