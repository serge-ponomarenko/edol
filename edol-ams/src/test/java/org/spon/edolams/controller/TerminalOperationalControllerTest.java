package org.spon.edolams.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolams.model.AmsStatus;
import org.spon.edolams.service.AmsTenantContext;
import org.spon.edolams.service.InvalidTerminalCredentialException;
import org.spon.edolams.service.TerminalAuthenticationService;
import org.spon.edolams.service.TerminalOperationalService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalOperationalControllerTest {

    private static final UUID TERMINAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000094");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000095");
    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000096");
    private static final String AUTHORIZATION = "EDOL-Terminal credential";

    @Mock
    private TerminalAuthenticationService authenticationService;

    @Mock
    private TerminalOperationalService operationalService;

    @Test
    void returnsStateForThePrinterBoundToTheAuthenticatedTerminal() {
        TerminalAuthenticationService.AuthenticatedTerminal terminal =
                new TerminalAuthenticationService.AuthenticatedTerminal(TERMINAL_ID, TENANT_ID, PRINTER_ID, null, 1);
        AmsStatus expected = new AmsStatus();
        when(authenticationService.authenticate(AUTHORIZATION)).thenReturn(terminal);
        when(operationalService.state(terminal)).thenReturn(expected);

        AmsStatus actual = controller().state(AUTHORIZATION);

        assertThat(actual).isSameAs(expected);
        verify(authenticationService).authenticate(AUTHORIZATION);
        verify(operationalService).state(terminal);
    }

    @Test
    void exposesRejectedTerminalCredentialsAsUnauthorized() {
        when(authenticationService.authenticate(AUTHORIZATION)).thenThrow(new InvalidTerminalCredentialException());

        assertThatThrownBy(() -> controller().state(AUTHORIZATION))
                .isInstanceOf(InvalidTerminalCredentialException.class);
        var response = controller().invalidCredential();

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("EDOL-Terminal");
        verify(operationalService, never()).state(org.mockito.ArgumentMatchers.any());
    }

    private TerminalOperationalController controller() {
        return new TerminalOperationalController(authenticationService, new AmsTenantContext(), operationalService);
    }
}
