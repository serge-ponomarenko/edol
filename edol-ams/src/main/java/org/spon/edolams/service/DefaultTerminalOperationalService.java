package org.spon.edolams.service;

import org.spon.edolams.controller.AmsStatusController;
import org.spon.edolams.controller.SpoolController;
import org.spon.edolams.model.AmsStatus;
import org.spon.edolams.model.Spool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** Production operational path backed by Core and Hub. */
@Service
@ConditionalOnProperty(
        name = "edol-ams.terminal-simulator.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DefaultTerminalOperationalService implements TerminalOperationalService {

    private final AmsStatusController statusController;
    private final SpoolController spoolController;

    public DefaultTerminalOperationalService(AmsStatusController statusController, SpoolController spoolController) {
        this.statusController = statusController;
        this.spoolController = spoolController;
    }

    @Override
    public AmsStatus state(TerminalAuthenticationService.AuthenticatedTerminal terminal) {
        return statusController.getStateForTrustedPrinter(terminal.printerId());
    }

    @Override
    public ResponseEntity<Spool> findSpool(
            TerminalAuthenticationService.AuthenticatedTerminal terminal,
            Long spoolId
    ) {
        return spoolController.findSpoolForTrustedPrinter(spoolId, terminal.printerId());
    }

    @Override
    public ResponseEntity<String> setSpool(
            TerminalAuthenticationService.AuthenticatedTerminal terminal,
            Long spoolId,
            Integer slot
    ) {
        return spoolController.setSpoolForTrustedPrinter(spoolId, slot, terminal.printerId());
    }
}
