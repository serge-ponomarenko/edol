package org.spon.edolams.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AmsSpoolChangerService {

    private static final long SPOOL_TIMEOUT_MS = 60_000;

    private final RestClient edolHubClient;

    private Long spoolIdScanned = -1L;
    private Long timeSpoolScanned = -1L;

    public AmsSpoolChangerService(
            @Qualifier("edolHubRestClient") RestClient edolHubClient
    ) {
        this.edolHubClient = edolHubClient;
    }

    public void setSpoolScannedState(long spoolId) {
        timeSpoolScanned = System.currentTimeMillis();
        spoolIdScanned = spoolId;

        log.info("Spool {} scanned", spoolId);
    }

    public void setAmsSpoolIntoSlot(int slot) {
        if (isSpoolExpired()) {
            resetScannedSpool();
            log.warn("Time for Spool staging is expired");
            return;
        }

        if (spoolIdScanned != -1) {
            edolHubClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/s/{id}/{slot}")
                            .build(spoolIdScanned, slot))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Spool ID {} has been set into slot: {}", spoolIdScanned, slot);

            resetScannedSpool();
        } else {
            log.warn("No scanned spool");
        }
    }

    @Scheduled(fixedRate = 5000)
    public void clearExpiredSpool() {
        if (spoolIdScanned == -1) {
            return;
        }
        if (isSpoolExpired()) {
            log.warn("Scanned spool {} expired", spoolIdScanned);
            resetScannedSpool();
        }
    }

    public void resetScannedSpool() {
        spoolIdScanned = -1L;
        timeSpoolScanned = -1L;
    }

    private boolean isSpoolExpired() {
        return timeSpoolScanned != -1
                && (System.currentTimeMillis() - timeSpoolScanned) > SPOOL_TIMEOUT_MS;
    }

}