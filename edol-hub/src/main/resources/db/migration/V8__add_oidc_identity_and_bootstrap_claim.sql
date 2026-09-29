CREATE FUNCTION hub.current_oidc_issuer()
RETURNS text
LANGUAGE sql
STABLE
AS
$$
    SELECT nullif(current_setting('edol.oidc_issuer', true), '')
$$;

CREATE FUNCTION hub.current_oidc_subject()
RETURNS text
LANGUAGE sql
STABLE
AS
$$
    SELECT nullif(current_setting('edol.oidc_subject', true), '')
$$;

CREATE FUNCTION hub.legacy_claim_context_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS
$$
    SELECT current_setting('edol.legacy_claim_context', true) = 'true'
$$;

ALTER TABLE hub.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.users FORCE ROW LEVEL SECURITY;
CREATE POLICY users_identity_isolation ON hub.users
    USING ((issuer = hub.current_oidc_issuer() AND subject = hub.current_oidc_subject())
        OR hub.legacy_claim_context_active())
    WITH CHECK ((issuer = hub.current_oidc_issuer() AND subject = hub.current_oidc_subject())
        OR hub.legacy_claim_context_active());

ALTER TABLE hub.tenant_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.tenant_memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_memberships_identity_isolation ON hub.tenant_memberships
    USING (hub.legacy_claim_context_active() OR EXISTS (
        SELECT 1 FROM hub.users user_record
        WHERE user_record.id = tenant_memberships.user_id
          AND user_record.issuer = hub.current_oidc_issuer()
          AND user_record.subject = hub.current_oidc_subject()
    ))
    WITH CHECK (hub.legacy_claim_context_active() OR EXISTS (
        SELECT 1 FROM hub.users user_record
        WHERE user_record.id = tenant_memberships.user_id
          AND user_record.issuer = hub.current_oidc_issuer()
          AND user_record.subject = hub.current_oidc_subject()
    ));

DROP POLICY tenants_tenant_isolation ON hub.tenants;
CREATE POLICY tenants_tenant_or_membership_isolation ON hub.tenants
    USING (
        id = hub.current_tenant_id()
        OR (is_default AND hub.legacy_claim_context_active())
        OR EXISTS (
            SELECT 1
            FROM hub.tenant_memberships membership
            JOIN hub.users user_record ON user_record.id = membership.user_id
            WHERE membership.tenant_id = tenants.id
              AND membership.status = 'ACTIVE'
              AND user_record.issuer = hub.current_oidc_issuer()
              AND user_record.subject = hub.current_oidc_subject()
        )
    )
    WITH CHECK (id = hub.current_tenant_id());

CREATE TABLE hub.legacy_tenant_bootstrap_state
(
    singleton           boolean                     NOT NULL PRIMARY KEY DEFAULT true CHECK (singleton),
    claim_open          boolean                     NOT NULL DEFAULT false,
    claimed_by_user_id  uuid,
    claimed_at          timestamp with time zone,
    CONSTRAINT fk_legacy_tenant_bootstrap_state_user
        FOREIGN KEY (claimed_by_user_id) REFERENCES hub.users (id),
    CONSTRAINT chk_legacy_tenant_bootstrap_state_claim
        CHECK ((claim_open AND claimed_by_user_id IS NULL AND claimed_at IS NULL)
            OR (NOT claim_open))
);

ALTER TABLE hub.tenants NO FORCE ROW LEVEL SECURITY;
INSERT INTO hub.legacy_tenant_bootstrap_state (singleton, claim_open)
SELECT true, false
WHERE EXISTS (SELECT 1 FROM hub.tenants WHERE is_default)
ON CONFLICT (singleton) DO NOTHING;
ALTER TABLE hub.tenants FORCE ROW LEVEL SECURITY;

CREATE FUNCTION hub.claim_legacy_tenant_owner(
    requested_issuer text,
    requested_subject text,
    requested_display_name text,
    requested_email text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = hub, pg_temp
AS
$$
DECLARE
    claim_state hub.legacy_tenant_bootstrap_state%ROWTYPE;
    legacy_tenant_id uuid;
    user_id uuid;
BEGIN
    PERFORM set_config('edol.legacy_claim_context', 'true', true);

    SELECT * INTO claim_state
    FROM hub.legacy_tenant_bootstrap_state
    WHERE singleton
    FOR UPDATE;

    IF NOT FOUND OR NOT claim_state.claim_open THEN
        RAISE EXCEPTION 'Legacy tenant bootstrap window is closed';
    END IF;

    IF EXISTS (SELECT 1 FROM hub.users) OR EXISTS (SELECT 1 FROM hub.tenant_memberships) THEN
        RAISE EXCEPTION 'Legacy tenant bootstrap requires zero users and memberships';
    END IF;

    SELECT id INTO legacy_tenant_id
    FROM hub.tenants
    WHERE is_default;

    IF legacy_tenant_id IS NULL THEN
        RAISE EXCEPTION 'Legacy default tenant is unavailable';
    END IF;

    IF (SELECT count(*) FROM hub.tenants WHERE is_default) <> 1 THEN
        RAISE EXCEPTION 'Legacy tenant bootstrap requires exactly one default tenant';
    END IF;

    PERFORM set_config('edol.tenant_id', legacy_tenant_id::text, true);

    user_id := gen_random_uuid();
    INSERT INTO hub.users (id, issuer, subject, display_name, email, created_at, updated_at)
    VALUES (user_id, requested_issuer, requested_subject, requested_display_name, requested_email, current_timestamp, current_timestamp);

    INSERT INTO hub.tenant_memberships (id, tenant_id, user_id, role, status, created_at, updated_at)
    VALUES (gen_random_uuid(), legacy_tenant_id, user_id, 'OWNER', 'ACTIVE', current_timestamp, current_timestamp);

    UPDATE hub.tenants SET is_default = false WHERE id = legacy_tenant_id;
    UPDATE hub.legacy_tenant_bootstrap_state
    SET claim_open = false, claimed_by_user_id = user_id, claimed_at = current_timestamp
    WHERE singleton;

    RETURN legacy_tenant_id;
END;
$$;

REVOKE ALL ON FUNCTION hub.claim_legacy_tenant_owner(text, text, text, text) FROM PUBLIC;

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hub_runtime') THEN
        GRANT EXECUTE ON FUNCTION hub.current_oidc_issuer() TO hub_runtime;
        GRANT EXECUTE ON FUNCTION hub.current_oidc_subject() TO hub_runtime;
        GRANT EXECUTE ON FUNCTION hub.legacy_claim_context_active() TO hub_runtime;
        GRANT EXECUTE ON FUNCTION hub.claim_legacy_tenant_owner(text, text, text, text) TO hub_runtime;
        GRANT SELECT, INSERT, UPDATE, DELETE ON hub.legacy_tenant_bootstrap_state TO hub_runtime;
    END IF;
END;
$$;
