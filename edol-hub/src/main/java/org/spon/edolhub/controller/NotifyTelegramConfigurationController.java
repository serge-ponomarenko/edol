package org.spon.edolhub.controller;

import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.TenantTelegramSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class NotifyTelegramConfigurationController {
    private final TenantTelegramSettingsService settings;
    private final TenantContext tenants;
    private final String clientId;

    public NotifyTelegramConfigurationController(TenantTelegramSettingsService settings, TenantContext tenants,
            @Value("${edol-hub.security.trusted-notify-client-id:edol-notify-service}") String clientId) {
        this.settings = settings;
        this.tenants = tenants;
        this.clientId = clientId;
    }

    @GetMapping("/internal/notify/telegram/tenants")
    public ResponseEntity<List<UUID>> tenants(Authentication authentication) {
        authorize(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.configuredTenants());
    }

    @GetMapping("/internal/notify/telegram/settings")
    public ResponseEntity<TenantTelegramSettingsService.RuntimeSettings> settings(Authentication authentication,
            @RequestHeader("X-EDOL-Tenant-Id") List<String> tenantHeaders) {
        authorize(authentication);
        UUID tenantId = parseTenant(tenantHeaders);
        try (TenantContext.TenantScope ignored = tenants.open(tenantId)) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.runtimeSettings());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram settings unavailable");
        }
    }

    private UUID parseTenant(List<String> headers) {
        try {
            if (headers.size() == 1) {
                return UUID.fromString(headers.getFirst());
            }
        } catch (IllegalArgumentException exception) {
            // Use a fixed response without reflecting the supplied header.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly one valid tenant header is required");
    }

    private void authorize(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !clientId.equals(jwt.getToken().getClaimAsString("azp"))
                || jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_hub.notify.read"))
                || jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("SCOPE_tenant.context"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notify configuration access denied");
        }
    }
}
