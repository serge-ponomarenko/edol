package org.spon.edolams.service;

import com.github.f4b6a3.uuid.UuidCreator;
import org.spon.edolams.model.terminal.AmsTerminal;
import org.spon.edolams.model.terminal.PairingState;
import org.spon.edolams.model.terminal.TerminalLifecycle;
import org.spon.edolams.model.terminal.TerminalPairing;
import org.spon.edolams.repository.AmsTerminalRepository;
import org.spon.edolams.repository.TerminalPairingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalPairingManagementService {

    private static final Duration PAIRING_LIFETIME = Duration.ofMinutes(5);
    private final AmsTerminalRepository terminalRepository;
    private final TerminalPairingRepository pairingRepository;
    private final AmsTenantContext tenantContext;
    private final TerminalCredentialCodec credentialCodec;
    private final Clock clock;

    @Autowired
    public TerminalPairingManagementService(
            AmsTerminalRepository terminalRepository,
            TerminalPairingRepository pairingRepository,
            AmsTenantContext tenantContext,
            TerminalCredentialCodec credentialCodec
    ) {
        this(terminalRepository, pairingRepository, tenantContext, credentialCodec, Clock.systemUTC());
    }

    TerminalPairingManagementService(
            AmsTerminalRepository terminalRepository,
            TerminalPairingRepository pairingRepository,
            AmsTenantContext tenantContext,
            TerminalCredentialCodec credentialCodec,
            Clock clock
    ) {
        this.terminalRepository = terminalRepository;
        this.pairingRepository = pairingRepository;
        this.tenantContext = tenantContext;
        this.credentialCodec = credentialCodec;
        this.clock = clock;
    }

    @Transactional
    public CreatedPairing createPairing(UUID printerId) {
        UUID tenantId = tenantContext.currentTenantId();
        Instant now = clock.instant();
        terminalRepository.findFirstByTenantIdAndAllowedPrinterIdAndLifecycleStateInOrderByCreatedAtDescIdDesc(
                        tenantId,
                        printerId,
                        List.of(TerminalLifecycle.PENDING, TerminalLifecycle.ACTIVE)
                )
                .ifPresent(terminal -> retireRecoverablePendingTerminal(terminal, now));

        AmsTerminal terminal = new AmsTerminal();
        terminal.setId(UuidCreator.getTimeOrderedEpoch());
        terminal.setTenantId(tenantId);
        terminal.setAllowedPrinterId(printerId);
        terminal.setLifecycleState(TerminalLifecycle.PENDING);
        terminal.setCreatedAt(now);
        try {
            terminalRepository.saveAndFlush(terminal);
        } catch (DataIntegrityViolationException exception) {
            throw new TerminalLifecycleConflictException(
                    "A terminal pairing is already being created for this printer"
            );
        }

        String code = credentialCodec.newPairingCode();
        TerminalPairing pairing = new TerminalPairing();
        pairing.setId(UuidCreator.getTimeOrderedEpoch());
        pairing.setTerminalId(terminal.getId());
        pairing.setTenantId(tenantId);
        pairing.setAllowedPrinterId(printerId);
        pairing.setCodeDigest(credentialCodec.pairingCodeDigest(code));
        pairing.setCodeKeyVersion(credentialCodec.keyVersion());
        pairing.setFailedAttemptCount(0);
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
        AmsTerminal replacedTerminal = terminalRepository.findFirstByTenantIdAndAllowedPrinterIdAndLifecycleStateInOrderByCreatedAtDescIdDesc(
                        tenantContext.currentTenantId(),
                        printerId,
                        List.of(TerminalLifecycle.PENDING, TerminalLifecycle.ACTIVE, TerminalLifecycle.REVOKED)
                )
                .orElseThrow(() -> new TerminalNotFoundException(printerId));
        if (replacedTerminal.getLifecycleState() != TerminalLifecycle.REVOKED) {
            replacedTerminal.setCredentialDigest(null);
            replacedTerminal.setCredentialKeyVersion(null);
            pairingRepository.transitionPendingPairing(replacedTerminal.getId(), PairingState.REVOKED);
        }
        replacedTerminal.setLifecycleState(TerminalLifecycle.REPLACED);
        CreatedPairing replacement = createPairing(printerId);
        replacedTerminal.setReplacedByTerminalId(replacement.terminalId());
        return replacement;
    }

    @Transactional
    public CreatedPairing rotate(UUID printerId) {
        retireLiveTerminal(printerId, TerminalLifecycle.REVOKED);
        return createPairing(printerId);
    }

    @Transactional(readOnly = true)
    public TerminalStatus status(UUID printerId) {
        return terminalRepository.findFirstByTenantIdAndAllowedPrinterIdOrderByCreatedAtDesc(
                        tenantContext.currentTenantId(),
                        printerId
                )
                .map(terminal -> {
                    TerminalPairing pairing = pairingRepository
                            .findTopByTerminalIdOrderByCreatedAtDesc(terminal.getId())
                            .orElse(null);
                    return new TerminalStatus(
                            terminal.getId(),
                            terminal.getLifecycleState(),
                            terminal.getCreatedAt(),
                            terminal.getActivatedAt(),
                            terminal.getLastAuthenticatedAt(),
                            terminal.getRevokedAt(),
                            terminal.getResetAt(),
                            terminal.getReplacedByTerminalId(),
                            effectivePairingState(pairing),
                            pairing == null ? null : pairing.getExpiresAt()
                    );
                })
                .orElse(null);
    }

    private PairingState effectivePairingState(TerminalPairing pairing) {
        if (pairing == null) {
            return null;
        }
        if (pairing.getState() == PairingState.PENDING && !pairing.getExpiresAt().isAfter(clock.instant())) {
            return PairingState.EXPIRED;
        }
        return pairing.getState();
    }

    private AmsTerminal retireLiveTerminal(UUID printerId, TerminalLifecycle target) {
        AmsTerminal terminal = terminalRepository.findFirstByTenantIdAndAllowedPrinterIdAndLifecycleStateInOrderByCreatedAtDescIdDesc(
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

    private void retireRecoverablePendingTerminal(AmsTerminal terminal, Instant now) {
        if (terminal.getLifecycleState() == TerminalLifecycle.ACTIVE) {
            throw new TerminalLifecycleConflictException("A live terminal already exists for this printer");
        }
        TerminalPairing pairing = pairingRepository.findTopByTerminalIdOrderByCreatedAtDesc(terminal.getId())
                .orElseThrow(() -> new TerminalLifecycleConflictException("Pending terminal has no pairing"));
        PairingState state = pairing.getState();
        if (state == PairingState.PENDING && !pairing.getExpiresAt().isAfter(now)) {
            pairing.setState(PairingState.EXPIRED);
            state = PairingState.EXPIRED;
        }
        if (state != PairingState.EXPIRED && state != PairingState.EXHAUSTED) {
            throw new TerminalLifecycleConflictException("A pending pairing already exists for this printer");
        }
        terminal.setLifecycleState(TerminalLifecycle.REVOKED);
        terminal.setRevokedAt(now);
        terminal.setCredentialDigest(null);
        terminal.setCredentialKeyVersion(null);
        terminalRepository.saveAndFlush(terminal);
    }

    public record CreatedPairing(UUID terminalId, UUID printerId, String pairingCode, Instant expiresAt) {
    }

    public record TerminalStatus(
            UUID terminalId,
            TerminalLifecycle lifecycleState,
            Instant createdAt,
            Instant activatedAt,
            Instant lastAuthenticatedAt,
            Instant revokedAt,
            Instant resetAt,
            UUID replacedByTerminalId,
            PairingState pairingState,
            Instant pairingExpiresAt
    ) {
    }
}
