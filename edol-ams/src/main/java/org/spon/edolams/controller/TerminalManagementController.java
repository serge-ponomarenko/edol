package org.spon.edolams.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.spon.edolams.service.TerminalLifecycleConflictException;
import org.spon.edolams.service.TerminalNotFoundException;
import org.spon.edolams.service.TerminalPairingManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/internal/terminals")
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalManagementController {

    private final TerminalPairingManagementService pairingManagementService;

    public TerminalManagementController(TerminalPairingManagementService pairingManagementService) {
        this.pairingManagementService = pairingManagementService;
    }

    @PostMapping("/pairings")
    public ResponseEntity<PairingResponse> createPairing(@Valid @RequestBody PrinterRequest request) {
        return pairingResponse(pairingManagementService.createPairing(request.printerId()));
    }

    @PostMapping("/rotate")
    public ResponseEntity<PairingResponse> rotate(@Valid @RequestBody PrinterRequest request) {
        return pairingResponse(pairingManagementService.rotate(request.printerId()));
    }

    @PostMapping("/replace")
    public ResponseEntity<PairingResponse> replace(@Valid @RequestBody PrinterRequest request) {
        return pairingResponse(pairingManagementService.replace(request.printerId()));
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(@Valid @RequestBody PrinterRequest request) {
        pairingManagementService.revoke(request.printerId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/factory-reset")
    public ResponseEntity<Void> factoryReset(@Valid @RequestBody PrinterRequest request) {
        pairingManagementService.factoryReset(request.printerId());
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(TerminalLifecycleConflictException.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(TerminalNotFoundException.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<PairingResponse> pairingResponse(TerminalPairingManagementService.CreatedPairing pairing) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PairingResponse(pairing.terminalId(), pairing.printerId(), pairing.pairingCode(), pairing.expiresAt()));
    }

    public record PrinterRequest(@NotNull UUID printerId) {
    }

    public record PairingResponse(UUID terminalId, UUID printerId, String pairingCode, Instant expiresAt) {
    }
}
