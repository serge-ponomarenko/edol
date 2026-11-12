package org.spon.edolnotify.service;

import org.spon.edol.model.PrinterState;
import org.spon.edolnotify.model.PrinterSummary;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class PrinterService {

    static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private final RestClient edolCoreClient;
    private final RestClient snapshotClient;

    public PrinterService(@Qualifier("edolCoreClient") RestClient edolCoreClient,
                          @Qualifier("edolCoreSnapshotClient") RestClient snapshotClient) {
        this.edolCoreClient = edolCoreClient;
        this.snapshotClient = snapshotClient;
    }

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

    public byte[] getStatusImage(UUID printerId) {
        try {
            return snapshotClient.get()
                    .uri("/api/printers/{printerId}/camera/status-image/content", printerId)
                    .accept(MediaType.IMAGE_JPEG)
                    .exchange((request, response) -> {
                        MediaType type = response.getHeaders().getContentType();
                        if (response.getStatusCode().value() != 200 || type == null
                                || !MediaType.IMAGE_JPEG.isCompatibleWith(type)
                                || response.getHeaders().getContentLength() > MAX_SNAPSHOT_BYTES) {
                            return null;
                        }
                        byte[] bytes = response.getBody().readNBytes(MAX_SNAPSHOT_BYTES + 1);
                        return isBoundedJpeg(bytes) ? bytes : null;
                    });
        } catch (RestClientException | InvalidMediaTypeException exception) {
            // Snapshot failure must not hide an already authorized printer's text status.
            // Never log response bodies or authentication/transport exception messages.
            return null;
        }
    }

    private boolean isBoundedJpeg(byte[] bytes) {
        return bytes.length >= 4 && bytes.length <= MAX_SNAPSHOT_BYTES
                && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[bytes.length - 2] == (byte) 0xff && bytes[bytes.length - 1] == (byte) 0xd9;
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
