package org.spon.edolhub.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-hub.agent.enabled:false}")
public class CoreAgentManagementClient {
    private static final String BASE = "/api/printers/{printerId}/agent";
    private final RestClient core;

    public CoreAgentManagementClient(@Qualifier("edolCoreRestClient") RestClient core) {
        this.core = core;
    }

    public Pairing pair(UUID printer, boolean rotate) {
        return core.post().uri(BASE + (rotate ? "/rotate" : "/pairings"), printer)
                .retrieve().body(Pairing.class);
    }

    public void revoke(UUID printer) {
        core.post().uri(BASE + "/revoke", printer).retrieve().toBodilessEntity();
    }

    public Status status(UUID printer) {
        try {
            return core.get().uri(BASE, printer).retrieve().body(Status.class);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return null;
            }
            throw exception;
        }
    }

    public record Pairing(UUID agentId, String pairingCode, Instant expiresAt) {
        @Override public String toString() { return "AgentPairing[REDACTED]"; }
    }
    public record Status(UUID agentId, String state, Instant createdAt, Instant expiresAt, Instant activatedAt) { }
}
