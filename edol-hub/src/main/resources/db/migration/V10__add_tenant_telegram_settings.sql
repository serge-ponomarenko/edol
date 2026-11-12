CREATE TABLE hub.telegram_settings
(
    tenant_id uuid PRIMARY KEY REFERENCES hub.tenants (id),
    enabled boolean NOT NULL DEFAULT false,
    chat_id bigint,
    encrypted_token text,
    token_fingerprint varchar(64) UNIQUE,
    CONSTRAINT telegram_enabled_configuration CHECK
        (NOT enabled OR (chat_id IS NOT NULL AND chat_id <> 0 AND encrypted_token IS NOT NULL)),
    CONSTRAINT telegram_token_pair CHECK
        ((encrypted_token IS NULL) = (token_fingerprint IS NULL))
);

ALTER TABLE hub.telegram_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.telegram_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY telegram_settings_tenant_isolation ON hub.telegram_settings
    USING (tenant_id = hub.current_tenant_id())
    WITH CHECK (tenant_id = hub.current_tenant_id());

-- A non-secret discovery directory. Credentials and chat IDs are never catalogued.
-- Notify reads tenant IDs before opening each tenant's isolated settings scope.
CREATE TABLE hub.telegram_tenant_directory
(
    tenant_id uuid PRIMARY KEY REFERENCES hub.tenants (id)
);
ALTER TABLE hub.telegram_tenant_directory ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub.telegram_tenant_directory FORCE ROW LEVEL SECURITY;
CREATE POLICY telegram_directory_read ON hub.telegram_tenant_directory FOR SELECT USING (true);
CREATE POLICY telegram_directory_insert ON hub.telegram_tenant_directory FOR INSERT
    WITH CHECK (tenant_id = hub.current_tenant_id());
CREATE POLICY telegram_directory_delete ON hub.telegram_tenant_directory FOR DELETE
    USING (tenant_id = hub.current_tenant_id());
