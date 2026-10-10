package org.spon.edolams.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.Arrays;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalAuthenticationService {

    private final JdbcTemplate jdbcTemplate;
    private final TerminalCredentialCodec credentialCodec;

    public TerminalAuthenticationService(JdbcTemplate jdbcTemplate, TerminalCredentialCodec credentialCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.credentialCodec = credentialCodec;
    }

    public AuthenticatedTerminal authenticate(String authorization) {
        String credential = terminalCredential(authorization);
        int separator = credential.indexOf('.');
        if (separator < 1 || separator == credential.length() - 1 || credential.indexOf('.', separator + 1) != -1) {
            throw new InvalidTerminalCredentialException();
        }

        UUID terminalId;
        try {
            terminalId = UUID.fromString(credential.substring(0, separator));
        } catch (IllegalArgumentException exception) {
            throw new InvalidTerminalCredentialException();
        }
        String secret = credential.substring(separator + 1);
        List<AuthenticatedTerminal> terminals = jdbcTemplate.query(
                "select terminal_id, tenant_id, allowed_printer_id, credential_digest, credential_key_version "
                        + "from ams.find_terminal_credential(?)",
                (resultSet, rowNumber) -> new AuthenticatedTerminal(
                        resultSet.getObject("terminal_id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("allowed_printer_id", UUID.class),
                        resultSet.getBytes("credential_digest"),
                        resultSet.getInt("credential_key_version")
                ),
                terminalId
        );
        if (terminals.isEmpty()) {
            throw new InvalidTerminalCredentialException();
        }

        AuthenticatedTerminal terminal = terminals.getFirst();
        if (terminal.credentialDigest() == null
                || !credentialCodec.matchesTerminalCredential(terminal.credentialDigest(), terminal.credentialKeyVersion(), secret)) {
            throw new InvalidTerminalCredentialException();
        }
        jdbcTemplate.update("select ams.record_terminal_authentication(?)", terminalId);
        return terminal.withoutCredentialDigest();
    }

    private String terminalCredential(String authorization) {
        if (authorization == null || !authorization.startsWith("EDOL-Terminal ")) {
            throw new InvalidTerminalCredentialException();
        }
        return authorization.substring("EDOL-Terminal ".length());
    }

    public record AuthenticatedTerminal(
            UUID terminalId,
            UUID tenantId,
            UUID printerId,
            byte[] credentialDigest,
            int credentialKeyVersion
    ) {
        AuthenticatedTerminal withoutCredentialDigest() {
            return new AuthenticatedTerminal(terminalId, tenantId, printerId, null, credentialKeyVersion);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AuthenticatedTerminal terminal)) {
                return false;
            }
            return credentialKeyVersion == terminal.credentialKeyVersion
                    && terminalId.equals(terminal.terminalId)
                    && tenantId.equals(terminal.tenantId)
                    && printerId.equals(terminal.printerId)
                    && Arrays.equals(credentialDigest, terminal.credentialDigest);
        }

        @Override
        public int hashCode() {
            int result = java.util.Objects.hash(terminalId, tenantId, printerId, credentialKeyVersion);
            return 31 * result + Arrays.hashCode(credentialDigest);
        }

        @Override
        public String toString() {
            return "AuthenticatedTerminal[terminalId=" + terminalId
                    + ", tenantId=" + tenantId
                    + ", printerId=" + printerId
                    + ", credentialDigest=<redacted>"
                    + ", credentialKeyVersion=" + credentialKeyVersion + ']';
        }
    }
}
