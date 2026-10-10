ALTER TABLE ams.terminal_pairings
    ADD COLUMN failed_attempt_count integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT terminal_pairings_failed_attempt_count_check CHECK (failed_attempt_count >= 0);

ALTER TABLE ams.terminal_pairings
    DROP CONSTRAINT terminal_pairings_state_check,
    ADD CONSTRAINT terminal_pairings_state_check
        CHECK (state IN ('PENDING', 'CONSUMED', 'REVOKED', 'EXPIRED', 'EXHAUSTED'));

DROP FUNCTION ams.consume_terminal_pairing(uuid, bytea, bytea, integer);

CREATE FUNCTION ams.consume_terminal_pairing(
    p_printer_id uuid,
    p_current_code_digest bytea,
    p_current_code_key_version integer,
    p_previous_code_digest bytea,
    p_previous_code_key_version integer,
    p_credential_digest bytea,
    p_credential_key_version integer
) RETURNS TABLE (terminal_id uuid, tenant_id uuid, allowed_printer_id uuid)
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = ams, pg_temp
AS $$
DECLARE
    pairing ams.terminal_pairings%ROWTYPE;
    code_matches boolean;
BEGIN
    SELECT * INTO pairing
    FROM ams.terminal_pairings pairing_row
    WHERE pairing_row.allowed_printer_id = p_printer_id
      AND pairing_row.state = 'PENDING'
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF pairing.expires_at <= now() THEN
        UPDATE ams.terminal_pairings
        SET state = 'EXPIRED'
        WHERE id = pairing.id;
        RETURN;
    END IF;

    IF pairing.failed_attempt_count >= 5 THEN
        UPDATE ams.terminal_pairings
        SET state = 'EXHAUSTED'
        WHERE id = pairing.id;
        RETURN;
    END IF;

    code_matches := COALESCE(pairing.code_key_version = p_current_code_key_version
        AND pairing.code_digest = p_current_code_digest, false)
        OR COALESCE(p_previous_code_key_version IS NOT NULL
            AND pairing.code_key_version = p_previous_code_key_version
            AND pairing.code_digest = p_previous_code_digest, false);

    IF NOT code_matches THEN
        UPDATE ams.terminal_pairings
        SET failed_attempt_count = failed_attempt_count + 1,
            state = CASE WHEN failed_attempt_count + 1 >= 5 THEN 'EXHAUSTED' ELSE state END
        WHERE id = pairing.id;
        RETURN;
    END IF;

    UPDATE ams.terminal_pairings
    SET state = 'CONSUMED', consumed_at = now()
    WHERE id = pairing.id;

    UPDATE ams.terminals
    SET lifecycle_state = 'ACTIVE',
        credential_digest = p_credential_digest,
        credential_key_version = p_credential_key_version,
        activated_at = now()
    WHERE id = pairing.terminal_id
      AND lifecycle_state = 'PENDING';

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Terminal pairing state is inconsistent';
    END IF;

    RETURN QUERY SELECT pairing.terminal_id, pairing.tenant_id, pairing.allowed_printer_id;
END;
$$;

REVOKE ALL ON FUNCTION ams.consume_terminal_pairing(uuid, bytea, integer, bytea, integer, bytea, integer) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ams_runtime') THEN
        GRANT EXECUTE ON FUNCTION ams.consume_terminal_pairing(uuid, bytea, integer, bytea, integer, bytea, integer)
            TO ams_runtime;
    END IF;
END;
$$;

GRANT USAGE, CREATE ON SCHEMA ams TO ams_terminal_authenticator;
GRANT SELECT, UPDATE ON ams.terminals, ams.terminal_pairings TO ams_terminal_authenticator;
ALTER FUNCTION ams.consume_terminal_pairing(uuid, bytea, integer, bytea, integer, bytea, integer)
    OWNER TO ams_terminal_authenticator;
ALTER FUNCTION ams.find_terminal_credential(uuid) OWNER TO ams_terminal_authenticator;
ALTER FUNCTION ams.record_terminal_authentication(uuid) OWNER TO ams_terminal_authenticator;
