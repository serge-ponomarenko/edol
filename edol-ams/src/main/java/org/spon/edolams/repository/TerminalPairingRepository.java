package org.spon.edolams.repository;

import org.spon.edolams.model.terminal.TerminalPairing;
import org.spon.edolams.model.terminal.PairingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TerminalPairingRepository extends JpaRepository<TerminalPairing, UUID> {

    @Modifying
    @Query("""
            update TerminalPairing pairing
            set pairing.state = :state
            where pairing.terminalId = :terminalId and pairing.state = 'PENDING'
            """)
    int transitionPendingPairing(
            @Param("terminalId") UUID terminalId,
            @Param("state") PairingState state
    );
}
