package org.spon.edolams.service;

import org.spon.edolams.model.AmsStatus;
import org.spon.edolams.model.Spool;
import org.springframework.http.ResponseEntity;

/** Provides operational data for the printer bound to an authenticated terminal. */
public interface TerminalOperationalService {

    AmsStatus state(TerminalAuthenticationService.AuthenticatedTerminal terminal);

    ResponseEntity<Spool> findSpool(TerminalAuthenticationService.AuthenticatedTerminal terminal, Long spoolId);

    ResponseEntity<String> setSpool(
            TerminalAuthenticationService.AuthenticatedTerminal terminal,
            Long spoolId,
            Integer slot
    );
}
