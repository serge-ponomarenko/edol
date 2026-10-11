CREATE UNIQUE INDEX terminal_pairings_one_pending_code_per_key
    ON ams.terminal_pairings (code_key_version, code_digest)
    WHERE state = 'PENDING';

CREATE FUNCTION ams.consume_terminal_pairing(
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
BEGIN
    SELECT * INTO pairing
    FROM ams.terminal_pairings pairing_row
    WHERE pairing_row.state = 'PENDING'
      AND (
          (pairing_row.code_key_version = p_current_code_key_version
              AND pairing_row.code_digest = p_current_code_digest)
          OR (p_previous_code_key_version IS NOT NULL
              AND pairing_row.code_key_version = p_previous_code_key_version
              AND pairing_row.code_digest = p_previous_code_digest)
      )
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

REVOKE ALL ON FUNCTION ams.consume_terminal_pairing(bytea, integer, bytea, integer, bytea, integer) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ams_runtime') THEN
        GRANT EXECUTE ON FUNCTION ams.consume_terminal_pairing(bytea, integer, bytea, integer, bytea, integer)
            TO ams_runtime;
    END IF;
END;
$$;

ALTER FUNCTION ams.consume_terminal_pairing(bytea, integer, bytea, integer, bytea, integer)
    OWNER TO ams_terminal_authenticator;
