package org.spon.edolnotify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "edol-notify.telegram.settings-source", havingValue = "hub")
public class HubTelegramSettingsClient {
    private final RestClient client;
    private final NotifyServiceAccessTokenProvider tokens;

    public HubTelegramSettingsClient(@Value("${edol-hub.url}") String url, NotifyServiceAccessTokenProvider tokens) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.tokens = tokens;
    }

    public List<UUID> tenants() {
        List<UUID> result = client.get().uri("/internal/notify/telegram/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessTokenValue())
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (result == null) {
            throw new IllegalStateException("Hub Telegram directory is unavailable");
        }
        return List.copyOf(result);
    }

    public Settings settings(UUID tenantId) {
        Settings result = client.get().uri("/internal/notify/telegram/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessTokenValue())
                .header("X-EDOL-Tenant-Id", tenantId.toString())
                .retrieve().body(Settings.class);
        if (result == null || (result.enabled() && (result.chatId() == null || result.chatId() == 0
                || result.botToken() == null || result.botToken().isBlank()))) {
            throw new IllegalStateException("Hub Telegram settings are unavailable");
        }
        return result;
    }

    public record Settings(boolean enabled, Long chatId, String botToken) {
        @Override
        public String toString() {
            return "Settings[redacted]";
        }
    }
}
