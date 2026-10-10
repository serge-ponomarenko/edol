package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolams.model.terminal.AmsTerminal;
import org.spon.edolams.model.terminal.PairingState;
import org.spon.edolams.model.terminal.TerminalLifecycle;
import org.spon.edolams.model.terminal.TerminalPairing;
import org.spon.edolams.repository.AmsTerminalRepository;
import org.spon.edolams.repository.TerminalPairingRepository;

import java.util.Collection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalPairingManagementServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000082");

    @Mock
    private AmsTerminalRepository terminalRepository;

    @Mock
    private TerminalPairingRepository pairingRepository;

    @Mock
    private TerminalCredentialCodec credentialCodec;

    @Test
    void replacementRevokesThePreviousPendingPairingAndLinksTheOldTerminal() {
        AmsTerminal previous = liveTerminal();
        when(terminalRepository.findByTenantIdAndAllowedPrinterIdAndLifecycleStateIn(
                eq(TENANT_ID), eq(PRINTER_ID), org.mockito.ArgumentMatchers.<Collection<TerminalLifecycle>>any()
        )).thenReturn(Optional.of(previous))
                .thenReturn(Optional.empty());
        when(credentialCodec.newPairingCode()).thenReturn("ABCDEFGHJK");
        when(credentialCodec.pairingCodeDigest("ABCDEFGHJK")).thenReturn(new byte[]{1});
        when(credentialCodec.keyVersion()).thenReturn(1);

        TerminalPairingManagementService.CreatedPairing replacement;
        AmsTenantContext tenantContext = new AmsTenantContext();
        try (AmsTenantContext.Scope ignored = tenantContext.open(TENANT_ID)) {
            replacement = service(tenantContext).replace(PRINTER_ID);
        }

        assertThat(previous.getLifecycleState()).isEqualTo(TerminalLifecycle.REPLACED);
        assertThat(previous.getCredentialDigest()).isNull();
        assertThat(previous.getCredentialKeyVersion()).isNull();
        assertThat(previous.getReplacedByTerminalId()).isEqualTo(replacement.terminalId());
        verify(pairingRepository).transitionPendingPairing(previous.getId(), PairingState.REVOKED);

        ArgumentCaptor<TerminalPairing> pairingCaptor = ArgumentCaptor.forClass(TerminalPairing.class);
        verify(pairingRepository).save(pairingCaptor.capture());
        assertThat(pairingCaptor.getValue().getTerminalId()).isEqualTo(replacement.terminalId());
        assertThat(pairingCaptor.getValue().getAllowedPrinterId()).isEqualTo(PRINTER_ID);
    }

    @Test
    void factoryResetInvalidatesAnyPendingPairingAndRemovesTheCredential() {
        AmsTerminal terminal = liveTerminal();
        when(terminalRepository.findByTenantIdAndAllowedPrinterIdAndLifecycleStateIn(
                TENANT_ID, PRINTER_ID, List.of(TerminalLifecycle.PENDING, TerminalLifecycle.ACTIVE)
        )).thenReturn(Optional.of(terminal));

        AmsTenantContext tenantContext = new AmsTenantContext();
        try (AmsTenantContext.Scope ignored = tenantContext.open(TENANT_ID)) {
            service(tenantContext).factoryReset(PRINTER_ID);
        }

        assertThat(terminal.getLifecycleState()).isEqualTo(TerminalLifecycle.RESET);
        assertThat(terminal.getCredentialDigest()).isNull();
        assertThat(terminal.getCredentialKeyVersion()).isNull();
        assertThat(terminal.getResetAt()).isNotNull();
        verify(pairingRepository).transitionPendingPairing(terminal.getId(), PairingState.REVOKED);
    }

    @Test
    void replacesAnExpiredPendingPairingWithANewPairing() {
        Instant now = Instant.parse("2026-10-10T13:00:00Z");
        AmsTerminal pendingTerminal = pendingTerminal();
        TerminalPairing expiredPairing = new TerminalPairing();
        expiredPairing.setTerminalId(pendingTerminal.getId());
        expiredPairing.setState(PairingState.PENDING);
        expiredPairing.setExpiresAt(now.minusSeconds(1));
        when(terminalRepository.findByTenantIdAndAllowedPrinterIdAndLifecycleStateIn(
                TENANT_ID, PRINTER_ID, List.of(TerminalLifecycle.PENDING, TerminalLifecycle.ACTIVE)
        )).thenReturn(Optional.of(pendingTerminal));
        when(pairingRepository.findTopByTerminalIdOrderByCreatedAtDesc(pendingTerminal.getId()))
                .thenReturn(Optional.of(expiredPairing));
        when(credentialCodec.newPairingCode()).thenReturn("ABCDEFGHJK");
        when(credentialCodec.pairingCodeDigest("ABCDEFGHJK")).thenReturn(new byte[]{1});
        when(credentialCodec.keyVersion()).thenReturn(1);

        AmsTenantContext tenantContext = new AmsTenantContext();
        try (AmsTenantContext.Scope ignored = tenantContext.open(TENANT_ID)) {
            service(tenantContext, now).createPairing(PRINTER_ID);
        }

        assertThat(expiredPairing.getState()).isEqualTo(PairingState.EXPIRED);
        assertThat(pendingTerminal.getLifecycleState()).isEqualTo(TerminalLifecycle.REVOKED);
        assertThat(pendingTerminal.getRevokedAt()).isEqualTo(now);
        verify(terminalRepository).saveAndFlush(pendingTerminal);
        verify(pairingRepository).save(any(TerminalPairing.class));
    }

    private TerminalPairingManagementService service(AmsTenantContext tenantContext) {
        return new TerminalPairingManagementService(terminalRepository, pairingRepository, tenantContext, credentialCodec);
    }

    private TerminalPairingManagementService service(AmsTenantContext tenantContext, Instant now) {
        return new TerminalPairingManagementService(
                terminalRepository, pairingRepository, tenantContext, credentialCodec, Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private AmsTerminal liveTerminal() {
        AmsTerminal terminal = new AmsTerminal();
        terminal.setId(UUID.fromString("00000000-0000-0000-0000-000000000083"));
        terminal.setTenantId(TENANT_ID);
        terminal.setAllowedPrinterId(PRINTER_ID);
        terminal.setLifecycleState(TerminalLifecycle.ACTIVE);
        terminal.setCredentialDigest(new byte[]{7});
        terminal.setCredentialKeyVersion(1);
        return terminal;
    }

    private AmsTerminal pendingTerminal() {
        AmsTerminal terminal = liveTerminal();
        terminal.setLifecycleState(TerminalLifecycle.PENDING);
        terminal.setCredentialDigest(null);
        terminal.setCredentialKeyVersion(null);
        return terminal;
    }
}
