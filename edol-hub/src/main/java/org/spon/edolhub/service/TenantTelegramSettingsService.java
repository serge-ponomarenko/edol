package org.spon.edolhub.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantTelegramSettingsService {
    private final JdbcTemplate jdbc;
    private final TenantContext tenants;
    private final TelegramTokenCipher cipher;

    public TenantTelegramSettingsService(JdbcTemplate jdbc, TenantContext tenants, TelegramTokenCipher cipher) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.cipher = cipher;
    }

    @Transactional(readOnly = true)
    public Status status() {
        Stored stored = stored();
        return new Status(stored.enabled(), stored.chatId(), stored.encryptedToken() != null, cipher.available());
    }

    @Transactional
    public void save(boolean enabled, Long chatId, String token, boolean clearToken) {
        UUID tenantId = tenants.getCurrentTenantId();
        if (token == null || token.length() > 256) {
            throw new IllegalArgumentException("Invalid Telegram settings");
        }
        String replacement = token.strip();
        if ((!replacement.isEmpty() && !replacement.matches("\\d{1,20}:[A-Za-z0-9_-]{20,200}"))
                || (clearToken && !replacement.isEmpty()) || (chatId != null && chatId == 0)) {
            throw new IllegalArgumentException("Invalid Telegram settings");
        }
        jdbc.queryForList("SELECT tenant_id FROM hub.telegram_settings WHERE tenant_id = ? FOR UPDATE", tenantId);
        Stored previous = stored();
        String encrypted = clearToken ? null : previous.encryptedToken();
        String fingerprint = clearToken ? null : previous.fingerprint();
        if (!replacement.isEmpty()) {
            encrypted = cipher.encrypt(tenantId, replacement);
            fingerprint = cipher.fingerprint(replacement);
        }
        validateEnabled(enabled, chatId, encrypted);
        jdbc.update("""
                INSERT INTO hub.telegram_settings (tenant_id, enabled, chat_id, encrypted_token, token_fingerprint)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET enabled = excluded.enabled, chat_id = excluded.chat_id,
                    encrypted_token = excluded.encrypted_token, token_fingerprint = excluded.token_fingerprint
                """, tenantId, enabled, chatId, encrypted, fingerprint);
        if (enabled) {
            jdbc.update("INSERT INTO hub.telegram_tenant_directory (tenant_id) VALUES (?) ON CONFLICT DO NOTHING", tenantId);
        } else {
            jdbc.update("DELETE FROM hub.telegram_tenant_directory WHERE tenant_id = ?", tenantId);
        }
    }

    private void validateEnabled(boolean enabled, Long chatId, String encrypted) {
        if (enabled && (chatId == null || encrypted == null || !cipher.available())) {
            throw new IllegalArgumentException("Enabled Telegram requires a chat, token and secret storage");
        }
    }

    // Intentionally outside a JPA transaction: this catalog contains tenant IDs only.
    public List<UUID> configuredTenants() {
        return jdbc.query("SELECT tenant_id FROM hub.telegram_tenant_directory ORDER BY tenant_id",
                (rs, row) -> rs.getObject(1, UUID.class));
    }

    @Transactional(readOnly = true)
    public RuntimeSettings runtimeSettings() {
        Stored stored = stored();
        if (!stored.enabled()) {
            return new RuntimeSettings(false, null, null);
        }
        return new RuntimeSettings(true, stored.chatId(), cipher.decrypt(tenants.getCurrentTenantId(), stored.encryptedToken()));
    }

    private Stored stored() {
        return jdbc.query("""
                SELECT enabled, chat_id, encrypted_token, token_fingerprint FROM hub.telegram_settings WHERE tenant_id = ?
                """, (rs, row) -> new Stored(rs.getBoolean(1), rs.getObject(2, Long.class), rs.getString(3), rs.getString(4)),
                tenants.getCurrentTenantId()).stream().findFirst().orElse(new Stored(false, null, null, null));
    }

    public record Status(boolean enabled, Long chatId, boolean tokenConfigured, boolean storageAvailable) { }

    public record RuntimeSettings(boolean enabled, Long chatId, String botToken) {
        @Override
        public String toString() {
            return "RuntimeSettings[redacted]";
        }
    }

    private record Stored(boolean enabled, Long chatId, String encryptedToken, String fingerprint) {
        @Override
        public String toString() {
            return "Stored[redacted]";
        }
    }
}
