-- Secure-only: Home does not install agent identities or require pairing.
CREATE TABLE core.agents (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    printer_id uuid NOT NULL REFERENCES core.printers(id) ON DELETE CASCADE,
    state varchar(16) NOT NULL CHECK (state IN ('PENDING', 'ACTIVE', 'REVOKED')),
    code_digest bytea UNIQUE,
    code_key_version integer,
    expires_at timestamptz,
    credential_digest bytea,
    credential_key_version integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    activated_at timestamptz,
    revoked_at timestamptz,
    CHECK ((state = 'PENDING' AND octet_length(code_digest) = 32 AND code_digest IS NOT NULL
            AND code_key_version IS NOT NULL AND code_key_version > 0 AND expires_at IS NOT NULL)
        OR (state <> 'PENDING' AND code_digest IS NULL AND code_key_version IS NULL)),
    CHECK ((state = 'ACTIVE' AND credential_digest IS NOT NULL AND octet_length(credential_digest) = 32
            AND credential_key_version IS NOT NULL AND credential_key_version > 0)
        OR (state <> 'ACTIVE' AND credential_digest IS NULL AND credential_key_version IS NULL))
);
CREATE UNIQUE INDEX agents_one_live_per_printer ON core.agents(printer_id)
    WHERE state IN ('PENDING', 'ACTIVE');
CREATE INDEX agents_tenant_printer ON core.agents(tenant_id, printer_id, created_at DESC);

ALTER TABLE core.agents ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.agents FORCE ROW LEVEL SECURITY;
CREATE POLICY agents_tenant_isolation ON core.agents
    USING (tenant_id = core.current_tenant_id() AND EXISTS (
        SELECT 1 FROM core.printers p WHERE p.id = printer_id AND p.tenant_id = agents.tenant_id))
    WITH CHECK (tenant_id = core.current_tenant_id() AND EXISTS (
        SELECT 1 FROM core.printers p WHERE p.id = printer_id AND p.tenant_id = agents.tenant_id));

-- No login, no runtime membership and no BYPASSRLS. Only the two fixed functions
-- below can exercise these role-specific cross-tenant policies.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_agent_authenticator') THEN
        CREATE ROLE core_agent_authenticator NOLOGIN NOINHERIT NOBYPASSRLS;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_agent_authenticator'
        AND (rolcanlogin OR rolsuper OR rolbypassrls OR rolinherit OR rolcreaterole OR rolcreatedb)) THEN
        RAISE EXCEPTION 'Agent function owner must be NOLOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB NOBYPASSRLS NOINHERIT';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_runtime') THEN
        IF pg_has_role('core_runtime', 'core_agent_authenticator', 'MEMBER') THEN
            RAISE EXCEPTION 'Core runtime must not be a member of the agent function-owner role';
        END IF;
    END IF;
END $$;
GRANT USAGE, CREATE ON SCHEMA core TO core_agent_authenticator;
GRANT SELECT, UPDATE ON core.agents TO core_agent_authenticator;
GRANT SELECT (id, tenant_id, enabled, connection_mode) ON core.printers TO core_agent_authenticator;
CREATE POLICY agents_authenticator_access ON core.agents TO core_agent_authenticator
    USING (true) WITH CHECK (true);
CREATE POLICY agents_authenticator_printer_read ON core.printers FOR SELECT TO core_agent_authenticator
    USING (true);

CREATE FUNCTION core.consume_agent_pairing(
    p_current_digest bytea, p_current_version integer,
    p_previous_digest bytea, p_previous_version integer,
    p_credential_digest bytea, p_credential_version integer
) RETURNS TABLE (agent_id uuid)
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, core, pg_temp AS $$
    UPDATE core.agents a SET state = 'ACTIVE', code_digest = NULL, code_key_version = NULL,
        credential_digest = p_credential_digest, credential_key_version = p_credential_version,
        activated_at = now()
    WHERE a.state = 'PENDING' AND a.expires_at > now()
      AND ((a.code_digest = p_current_digest AND a.code_key_version = p_current_version)
        OR (a.code_digest = p_previous_digest AND a.code_key_version = p_previous_version))
      AND EXISTS (SELECT 1 FROM core.printers p WHERE p.id = a.printer_id
          AND p.tenant_id = a.tenant_id AND p.enabled AND p.connection_mode = 'AGENT')
    RETURNING a.id;
$$;

CREATE FUNCTION core.find_agent_credential(p_agent_id uuid)
RETURNS TABLE (agent_id uuid, tenant_id uuid, printer_id uuid, credential_digest bytea, credential_key_version integer)
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, core, pg_temp AS $$
    SELECT a.id, a.tenant_id, a.printer_id, a.credential_digest, a.credential_key_version
    FROM core.agents a JOIN core.printers p ON p.id = a.printer_id AND p.tenant_id = a.tenant_id
    WHERE a.id = p_agent_id AND a.state = 'ACTIVE' AND p.enabled AND p.connection_mode = 'AGENT';
$$;
REVOKE ALL ON FUNCTION core.consume_agent_pairing(bytea, integer, bytea, integer, bytea, integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION core.find_agent_credential(uuid) FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_runtime') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON core.agents TO core_runtime;
        GRANT EXECUTE ON FUNCTION core.consume_agent_pairing(bytea, integer, bytea, integer, bytea, integer) TO core_runtime;
        GRANT EXECUTE ON FUNCTION core.find_agent_credential(uuid) TO core_runtime;
    END IF;
END $$;
ALTER FUNCTION core.consume_agent_pairing(bytea, integer, bytea, integer, bytea, integer)
    OWNER TO core_agent_authenticator;
ALTER FUNCTION core.find_agent_credential(uuid) OWNER TO core_agent_authenticator;
REVOKE CREATE ON SCHEMA core FROM core_agent_authenticator;
