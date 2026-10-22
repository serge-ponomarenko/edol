-- Tombstones deliberately survive printer/agent deletion until broker revocation is confirmed.
CREATE TABLE core.agent_mqtt_authorities (
    agent_id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    printer_id uuid NOT NULL,
    state varchar(24) NOT NULL CHECK (state IN ('PROVISIONING', 'READY', 'REVOKE_PENDING', 'REVOKED')),
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE core.agent_mqtt_authorities ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.agent_mqtt_authorities FORCE ROW LEVEL SECURITY;
CREATE POLICY agent_mqtt_tenant ON core.agent_mqtt_authorities
    USING (tenant_id = core.current_tenant_id()) WITH CHECK (tenant_id = core.current_tenant_id());
CREATE POLICY agent_mqtt_authenticator ON core.agent_mqtt_authorities TO core_agent_authenticator
    USING (true) WITH CHECK (true);
GRANT SELECT, UPDATE ON core.agent_mqtt_authorities TO core_agent_authenticator;
GRANT SELECT, INSERT, UPDATE ON core.agent_mqtt_authorities TO core_runtime;
GRANT USAGE, CREATE ON SCHEMA core TO core_agent_authenticator;

CREATE FUNCTION core.retire_agent_mqtt() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, core, pg_temp AS $$
BEGIN
    IF TG_TABLE_NAME = 'agents' THEN
        UPDATE core.agent_mqtt_authorities SET state = 'REVOKE_PENDING'
            WHERE agent_id = OLD.id AND state <> 'REVOKED';
    ELSE
        UPDATE core.agent_mqtt_authorities SET state = 'REVOKE_PENDING'
            WHERE printer_id = OLD.id AND state <> 'REVOKED';
    END IF;
    RETURN OLD;
END;
$$;
CREATE TRIGGER agent_mqtt_retire_update AFTER UPDATE OF state ON core.agents
    FOR EACH ROW WHEN (NEW.state = 'REVOKED') EXECUTE FUNCTION core.retire_agent_mqtt();
CREATE TRIGGER agent_mqtt_retire_delete BEFORE DELETE ON core.agents
    FOR EACH ROW EXECUTE FUNCTION core.retire_agent_mqtt();
CREATE TRIGGER printer_mqtt_retire_update AFTER UPDATE OF enabled, connection_mode, tenant_id ON core.printers
    FOR EACH ROW WHEN (NOT NEW.enabled OR NEW.connection_mode <> 'AGENT' OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id)
    EXECUTE FUNCTION core.retire_agent_mqtt();
CREATE TRIGGER printer_mqtt_retire_delete BEFORE DELETE ON core.printers
    FOR EACH ROW EXECUTE FUNCTION core.retire_agent_mqtt();

-- Fixed metadata-only discovery for Core ingress and broker revocation. No credential material.
CREATE FUNCTION core.agent_mqtt_catalog()
RETURNS TABLE(agent_id uuid, tenant_id uuid, printer_id uuid, state varchar, usable boolean)
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, core, pg_temp AS $$
    SELECT m.agent_id, m.tenant_id, m.printer_id, m.state,
        m.state = 'READY' AND a.state = 'ACTIVE' AND p.enabled AND p.connection_mode = 'AGENT'
            AND a.tenant_id = m.tenant_id AND a.printer_id = m.printer_id AND p.tenant_id = m.tenant_id
    FROM core.agent_mqtt_authorities m
    LEFT JOIN core.agents a ON a.id = m.agent_id
    LEFT JOIN core.printers p ON p.id = m.printer_id;
$$;
CREATE FUNCTION core.expire_agent_mqtt_provisioning() RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, core, pg_temp AS $$
    UPDATE core.agent_mqtt_authorities SET state = 'REVOKE_PENDING'
    WHERE state = 'PROVISIONING' AND created_at < now() - interval '1 minute';
$$;
CREATE FUNCTION core.confirm_agent_mqtt_revoked(p_id uuid) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, core, pg_temp AS $$
    UPDATE core.agent_mqtt_authorities SET state = 'REVOKED'
    WHERE agent_id = p_id AND state = 'REVOKE_PENDING';
$$;
REVOKE ALL ON FUNCTION core.retire_agent_mqtt() FROM PUBLIC;
REVOKE ALL ON FUNCTION core.agent_mqtt_catalog() FROM PUBLIC;
REVOKE ALL ON FUNCTION core.expire_agent_mqtt_provisioning() FROM PUBLIC;
REVOKE ALL ON FUNCTION core.confirm_agent_mqtt_revoked(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.agent_mqtt_catalog(), core.expire_agent_mqtt_provisioning(),
    core.confirm_agent_mqtt_revoked(uuid) TO core_runtime;
ALTER FUNCTION core.retire_agent_mqtt() OWNER TO core_agent_authenticator;
ALTER FUNCTION core.agent_mqtt_catalog() OWNER TO core_agent_authenticator;
ALTER FUNCTION core.expire_agent_mqtt_provisioning() OWNER TO core_agent_authenticator;
ALTER FUNCTION core.confirm_agent_mqtt_revoked(uuid) OWNER TO core_agent_authenticator;
REVOKE CREATE ON SCHEMA core FROM core_agent_authenticator;
