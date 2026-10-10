package org.spon.edolams.repository;

import org.spon.edolams.model.terminal.AmsTerminal;
import org.spon.edolams.model.terminal.TerminalLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AmsTerminalRepository extends JpaRepository<AmsTerminal, UUID> {

    Optional<AmsTerminal> findByTenantIdAndAllowedPrinterIdAndLifecycleStateIn(
            UUID tenantId,
            UUID printerId,
            Collection<TerminalLifecycle> lifecycleStates
    );
}
