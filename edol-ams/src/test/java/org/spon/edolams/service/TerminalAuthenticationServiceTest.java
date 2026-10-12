package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalAuthenticationServiceTest {

    private static final UUID TERMINAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000092");
    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000093");
    private static final String SECRET = "terminal-secret";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TerminalCredentialCodec credentialCodec;

    @Test
    void authenticatesAndRecordsLastConnectionThroughTheVoidFunctionQuery() {
        TerminalAuthenticationService.AuthenticatedTerminal stored = storedTerminal();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TerminalAuthenticationService.AuthenticatedTerminal>>any(), eq(TERMINAL_ID))).thenReturn(List.of(stored));
        when(credentialCodec.matchesTerminalCredential(stored.credentialDigest(), 1, SECRET)).thenReturn(true);

        TerminalAuthenticationService.AuthenticatedTerminal authenticated = service()
                .authenticate("EDOL-Terminal " + TERMINAL_ID + "." + SECRET);

        assertThat(authenticated.terminalId()).isEqualTo(TERMINAL_ID);
        assertThat(authenticated.tenantId()).isEqualTo(TENANT_ID);
        assertThat(authenticated.printerId()).isEqualTo(PRINTER_ID);
        assertThat(authenticated.credentialDigest()).isNull();
        verify(jdbcTemplate).query(
                eq("select ams.record_terminal_authentication(?)"),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Void>>any(),
                eq(TERMINAL_ID)
        );
    }

    @Test
    void rejectsAnInvalidTerminalSecretWithoutRecordingAConnection() {
        TerminalAuthenticationService.AuthenticatedTerminal stored = storedTerminal();
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TerminalAuthenticationService.AuthenticatedTerminal>>any(), eq(TERMINAL_ID))).thenReturn(List.of(stored));
        when(credentialCodec.matchesTerminalCredential(stored.credentialDigest(), 1, SECRET)).thenReturn(false);

        assertThatThrownBy(() -> service().authenticate("EDOL-Terminal " + TERMINAL_ID + "." + SECRET))
                .isInstanceOf(InvalidTerminalCredentialException.class);

        verify(jdbcTemplate, never()).query(
                eq("select ams.record_terminal_authentication(?)"),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Void>>any(),
                eq(TERMINAL_ID)
        );
    }

    @Test
    void rejectsARevokedTerminalWithoutRecordingAConnection() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TerminalAuthenticationService.AuthenticatedTerminal>>any(), eq(TERMINAL_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> service().authenticate("EDOL-Terminal " + TERMINAL_ID + "." + SECRET))
                .isInstanceOf(InvalidTerminalCredentialException.class);

        verify(jdbcTemplate, never()).query(
                eq("select ams.record_terminal_authentication(?)"),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Void>>any(),
                eq(TERMINAL_ID)
        );
    }

    private TerminalAuthenticationService service() {
        return new TerminalAuthenticationService(jdbcTemplate, credentialCodec);
    }

    private TerminalAuthenticationService.AuthenticatedTerminal storedTerminal() {
        return new TerminalAuthenticationService.AuthenticatedTerminal(
                TERMINAL_ID, TENANT_ID, PRINTER_ID, new byte[]{1, 2, 3}, 1
        );
    }
}
