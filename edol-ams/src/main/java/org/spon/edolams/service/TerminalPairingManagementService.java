package org.spon.edolams.service;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.spon.edolams.model.terminal.AmsTerminal;
import org.spon.edolams.model.terminal.PairingState;
import org.spon.edolams.model.terminal.TerminalLifecycle;
import org.spon.edolams.model.terminal.TerminalPairing;
import org.spon.edolams.repository.AmsTerminalRepository;
import org.spon.edolams.repository.TerminalPairingRepository;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalPairingManagementService {

    private static final Duration PAIRING_LIFETIME = Duration.ofMinutes(5);
    private final AmsTerminalRepository terminalRepository;
    private final TerminalPairingRepository pairingRepository;
    private final AmsTenantContext tenantContext;
    private final TerminalCredentialCodec credentialCodec;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public CreatedPairing createPairing(UUID printerId) {
        UUID tenantId = tenantContext.currentTenantId();
        terminalRepository.findByTenantIdAndAllowedPrinterIdAndLifecycleStateIn(
                        tenantId,
                        printerId,
                        List.of(TerminalLifecycle.PENDING, TerminalLifecycle.ACTIVE)
                )
                .ifPresent(terminal -> {
                    throw new TerminalLifecycleConflictException("A live terminal already exists for this printer");
                });

        Instant now = clock.instant();
        AmsTerminal terminal = new AmsTerminal();
        terminal.setId(UuidCreator.getTimeOrderedEpoch());
        terminal.setTenantId(tenantId);
        terminal.setAllowedPrinterId(printerId);
        terminal.setLifecycleState(TerminalLifecycle.PENDING);
        terminal.setCreatedAt(now);
        terminalRepository.save(terminal);

        String code = credentialCodec.newPairingCode();
        TerminalPairing pairing = new TerminalPairing();
        pairing.setId(UuidCreator.getTimeOrderedEpoch());
        pairing.setTerminalId(terminal.getId());
        pairing.setTenantId(tenantId);
        pairing.setAllowedPrinterId(printerId);
        pairing.setCodeDigest(credentialCodec.pairingCodeDigest(code));
        pairing.setCodeKeyVersion(credentialCodec.keyVersion());
        pairing.setState(PairingState.PENDING);
        pairing.setCreatedAt(now);
        pairing.setExpiresAt(now.plus(PAIRING_LIFETIME));
        pairingRepository.save(pairing);
        return new CreatedPairing(terminal.getId(), printerId, code, pairing.getExpiresAt());
    }

    @Transactional
    public void revoke(UUID printerId) {
        retireLiveTerminal(printerId, TerminalLifecycle.REVOKED);
    }

    @Transactional
    public void factoryReset(UUID printerId) {
        retireLiveTerminal(printerId, TerminalLifecycle.RESET);
    }

    @Transactional
    public CreatedPairing replace(UUID printerId) {
        AmsTerminal replacedTerminal = retireLiveTerminal(printerId, TerminalLifecycle.REPLACED);
        CreatedPairing replacement = createPairing(printerId);
        replacedTerminal.setReplacedByTerminalId(replacement.terminalId());
        return replacement;
    }

    @Transactional
    public CreatedPairing rotate(UUID printerId) {
        retireLiveTerminal(printerId, TerminalLifecycle.REVOKED);
        return createPairing(printerId);
    }

    private AmsTerminal retireLiveTerminal(UUID printerId, TerminalLifecycle target) {
        AmsTerminal terminal = terminalRepository.findByTenantIdAndAllowedPrinterIdAndLifecycleStateIn(
                        tenantContext.currentTenantId(),
                        printerId,
                        List.of(TerminalLifecycle.PENDING, TerminalLifecycle.ACTIVE)
                )
                .orElseThrow(() -> new TerminalNotFoundException(printerId));
        terminal.setLifecycleState(target);
        terminal.setCredentialDigest(null);
        terminal.setCredentialKeyVersion(null);
        pairingRepository.transitionPendingPairing(terminal.getId(), PairingState.REVOKED);
        Instant now = clock.instant();
        if (target == TerminalLifecycle.REVOKED) {
            terminal.setRevokedAt(now);
        } else if (target == TerminalLifecycle.RESET) {
            terminal.setResetAt(now);
        }
        return terminal;
    }

    public record CreatedPairing(UUID terminalId, UUID printerId, String pairingCode, Instant expiresAt) {
    }
}
