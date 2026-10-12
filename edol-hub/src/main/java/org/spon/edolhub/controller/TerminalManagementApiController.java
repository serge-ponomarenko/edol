package org.spon.edolhub.controller;

import jakarta.validation.constraints.NotNull;
import org.spon.edolhub.service.AmsTerminalManagementClient;
import org.spon.edolhub.service.AmsTerminalManagementConflictException;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/terminals")
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalManagementApiController {

    private final TenantOwnerAuthorizationService ownerAuthorizationService;
    private final PrinterAccessService printerAccessService;
    private final AmsTerminalManagementClient terminalManagementClient;

    public TerminalManagementApiController(
            TenantOwnerAuthorizationService ownerAuthorizationService,
            PrinterAccessService printerAccessService,
            AmsTerminalManagementClient terminalManagementClient
    ) {
        this.ownerAuthorizationService = ownerAuthorizationService;
        this.printerAccessService = printerAccessService;
        this.terminalManagementClient = terminalManagementClient;
    }

    @PostMapping("/{printerId}/pairings")
    public ResponseEntity<PairingResponse> createPairing(@PathVariable UUID printerId) {
        return pairingResponse(printerId, terminalManagementClient::createPairing);
    }

    @PostMapping("/{printerId}/rotate")
    public ResponseEntity<PairingResponse> rotate(@PathVariable UUID printerId) {
        return pairingResponse(printerId, terminalManagementClient::rotate);
    }

    @PostMapping("/{printerId}/replace")
    public ResponseEntity<PairingResponse> replace(@PathVariable UUID printerId) {
        return pairingResponse(printerId, terminalManagementClient::replace);
    }

    @PostMapping("/{printerId}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID printerId) {
        authorizePrinter(printerId);
        terminalManagementClient.revoke(printerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{printerId}/factory-reset")
    public ResponseEntity<Void> factoryReset(@PathVariable UUID printerId) {
        authorizePrinter(printerId);
        terminalManagementClient.factoryReset(printerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{printerId}")
    public ResponseEntity<AmsTerminalManagementClient.TerminalStatus> status(@PathVariable UUID printerId) {
        authorizePrinter(printerId);
        return terminalManagementClient.status(printerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AmsTerminalManagementConflictException.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
    }

    private ResponseEntity<PairingResponse> pairingResponse(
            UUID printerId,
            java.util.function.Function<UUID, AmsTerminalManagementClient.Pairing> operation
    ) {
        authorizePrinter(printerId);
        AmsTerminalManagementClient.Pairing pairing = operation.apply(printerId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PairingResponse(pairing.terminalId(), pairing.printerId(), pairing.pairingCode(), pairing.expiresAt()));
    }

    private void authorizePrinter(UUID printerId) {
        ownerAuthorizationService.requireCurrentOwner();
        try {
            printerAccessService.getPrinter(printerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Printer not found");
        }
    }

    public record PairingResponse(UUID terminalId, UUID printerId, String pairingCode, Instant expiresAt) {
    }
}
