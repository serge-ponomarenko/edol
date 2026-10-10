package org.spon.edolams.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalEnrollmentService {

    private final JdbcTemplate jdbcTemplate;
    private final TerminalCredentialCodec credentialCodec;

    public TerminalEnrollmentService(JdbcTemplate jdbcTemplate, TerminalCredentialCodec credentialCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.credentialCodec = credentialCodec;
    }

    public EnrollmentResult enroll(UUID printerId, String pairingCode) {
        String secret = credentialCodec.newTerminalSecret();
        byte[] credentialDigest = credentialCodec.terminalCredentialDigest(credentialCodec.keyVersion(), secret);
        TerminalCredentialCodec.PairingCodeDigests pairingDigests = credentialCodec.pairingCodeDigests(pairingCode);
        List<EnrollmentResult> rows = jdbcTemplate.query(
                "select terminal_id, tenant_id, allowed_printer_id from ams.consume_terminal_pairing(?, ?, ?, ?, ?, ?, ?)",
                (resultSet, rowNumber) -> mapEnrollment(resultSet, secret),
                printerId,
                pairingDigests.currentDigest(),
                pairingDigests.currentKeyVersion(),
                pairingDigests.previousDigest(),
                pairingDigests.previousKeyVersion(),
                credentialDigest,
                credentialCodec.keyVersion()
        );
        if (!rows.isEmpty()) {
            return rows.getFirst();
        }
        throw new InvalidTerminalPairingException();
    }

    private EnrollmentResult mapEnrollment(ResultSet resultSet, String secret) throws SQLException {
        return new EnrollmentResult(
                resultSet.getObject("terminal_id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("allowed_printer_id", UUID.class),
                secret
        );
    }

    public record EnrollmentResult(UUID terminalId, UUID tenantId, UUID printerId, String secret) {
    }
}
