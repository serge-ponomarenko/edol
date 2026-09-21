package org.spon.edolams.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AmsSpoolChangerService {

    private static final long SPOOL_TIMEOUT_MS = 60_000;

    private final RestClient edolHubClient;

    private final Map<UUID, ScannedSpool> scannedSpools = new ConcurrentHashMap<>();

    public AmsSpoolChangerService(
            @Qualifier("edolHubRestClient") RestClient edolHubClient
    ) {
        this.edolHubClient = edolHubClient;
    }

    public void setSpoolScannedState(UUID printerId, long spoolId) {
        scannedSpools.put(printerId, new ScannedSpool(spoolId, System.currentTimeMillis()));

        log.info("Spool {} scanned for printer {}", spoolId, printerId);
    }

    public void setAmsSpoolIntoSlot(UUID printerId, int slot) {
        ScannedSpool scannedSpool = scannedSpools.get(printerId);
        if (scannedSpool == null) {
            log.warn("No scanned spool for printer {}", printerId);
            return;
        }

        if (isExpired(scannedSpool)) {
            scannedSpools.remove(printerId, scannedSpool);
            log.warn("Time for spool staging is expired for printer {}", printerId);
            return;
        }

        edolHubClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/s/{printerId}/{spoolId}/{slot}")
                        .build(printerId, scannedSpool.spoolId(), slot))
                .retrieve()
                .toBodilessEntity();

        log.info("Spool ID {} has been set into slot {} for printer {}", scannedSpool.spoolId(), slot, printerId);
        scannedSpools.remove(printerId, scannedSpool);
    }

    @Scheduled(fixedRate = 5000)
    public void clearExpiredSpool() {
        scannedSpools.entrySet().removeIf(entry -> {
            if (!isExpired(entry.getValue())) {
                return false;
            }
            log.warn("Scanned spool {} expired for printer {}", entry.getValue().spoolId(), entry.getKey());
            return true;
        });
    }

    public void resetScannedSpool(UUID printerId) {
        scannedSpools.remove(printerId);
    }

    private boolean isExpired(ScannedSpool scannedSpool) {
        return System.currentTimeMillis() - scannedSpool.scannedAt() > SPOOL_TIMEOUT_MS;
    }

    private record ScannedSpool(long spoolId, long scannedAt) {
    }

}
