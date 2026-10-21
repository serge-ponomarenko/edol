package org.spon.edolcore.controller;

import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/printers/{printerId}/agent")
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false}")
public class AgentPairingManagementController {
    private final AgentPairingService agents;

    public AgentPairingManagementController(AgentPairingService agents) {
        this.agents = agents;
    }

    @PostMapping("/pairings")
    public ResponseEntity<AgentPairingService.Pairing> create(@PathVariable UUID printerId) {
        return secretResponse(agents.create(printerId, false));
    }

    @PostMapping("/rotate")
    public ResponseEntity<AgentPairingService.Pairing> rotate(@PathVariable UUID printerId) {
        return secretResponse(agents.create(printerId, true));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID printerId) {
        agents.revoke(printerId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping
    public ResponseEntity<AgentPairingService.Status> status(@PathVariable UUID printerId) {
        var status = agents.status(printerId);
        return status == null ? ResponseEntity.notFound().build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);
    }

    private ResponseEntity<AgentPairingService.Pairing> secretResponse(AgentPairingService.Pairing pairing) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pairing);
    }
}
