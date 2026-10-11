package org.spon.edolhub.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;
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

    public Optional<TerminalStatus> status(UUID printerId) {
        try {
            return Optional.ofNullable(amsClient.get()
                    .uri("/internal/terminals/{printerId}", printerId)
                    .retrieve()
                    .body(TerminalStatus.class));
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public void revoke(UUID printerId) {
        command("/internal/terminals/revoke", printerId);
    }

    public void factoryReset(UUID printerId) {
        command("/internal/terminals/factory-reset", printerId);
    }

    private Pairing pairing(String path, UUID printerId) {
        try {
            return amsClient.post()
                    .uri(path)
                    .body(new PrinterRequest(printerId))
                    .retrieve()
                    .body(Pairing.class);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new AmsTerminalManagementConflictException(
                        "The terminal already has an active pairing or enrollment"
                );
            }
            throw exception;
        }
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

    public record TerminalStatus(
            UUID terminalId,
            String lifecycleState,
            Instant createdAt,
            Instant activatedAt,
            Instant lastAuthenticatedAt,
            Instant revokedAt,
            Instant resetAt,
            UUID replacedByTerminalId,
            String pairingState,
            Instant pairingExpiresAt
    ) {
    }
}
