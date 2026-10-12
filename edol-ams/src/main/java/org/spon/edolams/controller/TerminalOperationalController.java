package org.spon.edolams.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.spon.edolams.model.AmsStatus;
import org.spon.edolams.model.Spool;
import org.spon.edolams.service.AmsTenantContext;
import org.spon.edolams.service.InvalidTerminalCredentialException;
import org.spon.edolams.service.TerminalAuthenticationService;
import org.spon.edolams.service.TerminalOperationalService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@RequestMapping("/api/terminal/v1")
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalOperationalController {

    private final TerminalAuthenticationService authenticationService;
    private final AmsTenantContext tenantContext;
    private final TerminalOperationalService operationalService;

    public TerminalOperationalController(
            TerminalAuthenticationService authenticationService,
            AmsTenantContext tenantContext,
            TerminalOperationalService operationalService
    ) {
        this.authenticationService = authenticationService;
        this.tenantContext = tenantContext;
        this.operationalService = operationalService;
    }

    @GetMapping("/state")
    public AmsStatus state(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return withTerminal(authorization, operationalService::state);
    }

    @GetMapping("/find")
    public ResponseEntity<Spool> find(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("id") Long spoolId
    ) {
        return withTerminal(authorization, terminal -> operationalService.findSpool(terminal, spoolId));
    }

    @PostMapping("/set-spool")
    public ResponseEntity<String> setSpool(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody SetSpoolRequest request
    ) {
        return withTerminal(authorization, terminal -> operationalService.setSpool(
                terminal, request.spoolId(), request.slot()
        ));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(InvalidTerminalCredentialException.class)
    ResponseEntity<Void> invalidCredential() {
        return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "EDOL-Terminal").build();
    }

    private <T> T withTerminal(String authorization, java.util.function.Function<TerminalAuthenticationService.AuthenticatedTerminal, T> action) {
        TerminalAuthenticationService.AuthenticatedTerminal terminal = authenticationService.authenticate(authorization);
        try (AmsTenantContext.Scope ignored = tenantContext.open(terminal.tenantId())) {
            return action.apply(terminal);
        }
    }

    public record SetSpoolRequest(@NotNull Long spoolId, @NotNull Integer slot) {
    }
}
