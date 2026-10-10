package org.spon.edolhub.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class AmsTerminalManagementClient {

    private final RestClient amsClient;

    public AmsTerminalManagementClient(@Qualifier("edolAmsRestClient") RestClient amsClient) {
        this.amsClient = amsClient;
    }

    public Pairing createPairing(UUID printerId) {
        return pairing("/internal/terminals/pairings", printerId);
    }

    public Pairing rotate(UUID printerId) {
        return pairing("/internal/terminals/rotate", printerId);
    }

    public Pairing replace(UUID printerId) {
        return pairing("/internal/terminals/replace", printerId);
    }

    public void revoke(UUID printerId) {
        command("/internal/terminals/revoke", printerId);
    }

    public void factoryReset(UUID printerId) {
        command("/internal/terminals/factory-reset", printerId);
    }

    private Pairing pairing(String path, UUID printerId) {
        return amsClient.post()
                .uri(path)
                .body(new PrinterRequest(printerId))
                .retrieve()
                .body(Pairing.class);
    }

    private void command(String path, UUID printerId) {
        amsClient.post()
                .uri(path)
                .body(new PrinterRequest(printerId))
                .retrieve()
                .toBodilessEntity();
    }

    private record PrinterRequest(UUID printerId) {
    }

    public record Pairing(UUID terminalId, UUID printerId, String pairingCode, Instant expiresAt) {
    }
}
