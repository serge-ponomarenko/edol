CREATE SCHEMA IF NOT EXISTS ams;

CREATE FUNCTION ams.current_tenant_id() RETURNS uuid
    LANGUAGE plpgsql STABLE
AS $$
DECLARE
    tenant_value text;
BEGIN
    tenant_value := current_setting('edol.tenant_id', true);
    IF tenant_value IS NULL OR tenant_value = '' THEN
        RAISE EXCEPTION 'EDOL tenant context is required';
    END IF;
    RETURN tenant_value::uuid;
END;
$$;

CREATE TABLE ams.terminals (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    allowed_printer_id uuid NOT NULL,
    lifecycle_state varchar(16) NOT NULL,
    credential_digest bytea,
    credential_key_version integer,
    created_at timestamptz NOT NULL,
    activated_at timestamptz,
    revoked_at timestamptz,
    reset_at timestamptz,
    replaced_by_terminal_id uuid,
    last_authenticated_at timestamptz,
    CONSTRAINT terminals_lifecycle_state_check CHECK (lifecycle_state IN ('PENDING', 'ACTIVE', 'REVOKED', 'REPLACED', 'RESET')),
    CONSTRAINT terminals_credential_state_check CHECK (
        (lifecycle_state = 'ACTIVE' AND credential_digest IS NOT NULL AND credential_key_version IS NOT NULL)
        OR lifecycle_state <> 'ACTIVE'
    )
);

CREATE TABLE ams.terminal_pairings (
    id uuid PRIMARY KEY,
    terminal_id uuid NOT NULL REFERENCES ams.terminals(id),
    tenant_id uuid NOT NULL,
    allowed_printer_id uuid NOT NULL,
    code_digest bytea NOT NULL,
    code_key_version integer NOT NULL,
    state varchar(16) NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT terminal_pairings_state_check CHECK (state IN ('PENDING', 'CONSUMED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT terminal_pairings_tenant_printer_check CHECK (tenant_id IS NOT NULL AND allowed_printer_id IS NOT NULL)
);

CREATE UNIQUE INDEX terminals_one_live_terminal_per_printer
    ON ams.terminals (tenant_id, allowed_printer_id)
    WHERE lifecycle_state IN ('PENDING', 'ACTIVE');
CREATE UNIQUE INDEX terminal_pairings_one_pending_per_terminal
    ON ams.terminal_pairings (terminal_id)
    WHERE state = 'PENDING';
CREATE INDEX terminal_pairings_expiry_idx ON ams.terminal_pairings (expires_at);
CREATE INDEX terminals_tenant_printer_idx ON ams.terminals (tenant_id, allowed_printer_id);

ALTER TABLE ams.terminals ENABLE ROW LEVEL SECURITY;
ALTER TABLE ams.terminals FORCE ROW LEVEL SECURITY;
CREATE POLICY terminals_tenant_isolation ON ams.terminals
    USING (tenant_id = ams.current_tenant_id())
    WITH CHECK (tenant_id = ams.current_tenant_id());

ALTER TABLE ams.terminal_pairings ENABLE ROW LEVEL SECURITY;
ALTER TABLE ams.terminal_pairings FORCE ROW LEVEL SECURITY;
CREATE POLICY terminal_pairings_tenant_isolation ON ams.terminal_pairings
    USING (tenant_id = ams.current_tenant_id())
    WITH CHECK (tenant_id = ams.current_tenant_id());

CREATE FUNCTION ams.consume_terminal_pairing(
    p_printer_id uuid,
    p_code_digest bytea,
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
    WHERE pairing_row.allowed_printer_id = p_printer_id
      AND pairing_row.code_digest = p_code_digest
      AND pairing_row.state = 'PENDING'
      AND pairing_row.expires_at > now()
    FOR UPDATE;

    IF NOT FOUND THEN
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

CREATE FUNCTION ams.find_terminal_credential(p_terminal_id uuid)
    RETURNS TABLE (
        terminal_id uuid,
        tenant_id uuid,
        allowed_printer_id uuid,
        credential_digest bytea,
        credential_key_version integer
    )
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = ams, pg_temp
AS $$
    SELECT id, tenant_id, allowed_printer_id, credential_digest, credential_key_version
    FROM ams.terminals
    WHERE id = p_terminal_id AND lifecycle_state = 'ACTIVE';
$$;

CREATE FUNCTION ams.record_terminal_authentication(p_terminal_id uuid)
    RETURNS void
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = ams, pg_temp
AS $$
    UPDATE ams.terminals SET last_authenticated_at = now() WHERE id = p_terminal_id AND lifecycle_state = 'ACTIVE';
$$;

REVOKE ALL ON FUNCTION ams.consume_terminal_pairing(uuid, bytea, bytea, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION ams.find_terminal_credential(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION ams.record_terminal_authentication(uuid) FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ams_runtime') THEN
        GRANT USAGE ON SCHEMA ams TO ams_runtime;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ams TO ams_runtime;
        GRANT EXECUTE ON FUNCTION ams.current_tenant_id() TO ams_runtime;
        GRANT EXECUTE ON FUNCTION ams.consume_terminal_pairing(uuid, bytea, bytea, integer) TO ams_runtime;
        GRANT EXECUTE ON FUNCTION ams.find_terminal_credential(uuid) TO ams_runtime;
        GRANT EXECUTE ON FUNCTION ams.record_terminal_authentication(uuid) TO ams_runtime;
    END IF;
END;
$$;
