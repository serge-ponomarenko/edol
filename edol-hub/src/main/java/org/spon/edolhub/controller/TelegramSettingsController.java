package org.spon.edolhub.controller;

import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.spon.edolhub.service.TenantTelegramSettingsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TelegramSettingsController {
    private static final String LOCATION = "redirect:/settings/telegram";
    private final TenantOwnerAuthorizationService owners;
    private final TenantTelegramSettingsService settings;

    public TelegramSettingsController(TenantOwnerAuthorizationService owners, TenantTelegramSettingsService settings) {
        this.owners = owners;
        this.settings = settings;
    }

    @GetMapping({"/settings", "/settings/telegram"})
    public String page(Model model, HttpServletResponse response) {
        owners.requireCurrentOwner();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("telegram", settings.status());
        return "dashboard/settings/telegram";
    }

    @PostMapping("/settings/telegram")
    public String save(@RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam(defaultValue = "") String chatId,
                       @RequestParam(defaultValue = "") String botToken,
                       @RequestParam(defaultValue = "false") boolean clearToken,
                       HttpServletResponse response) {
        owners.requireCurrentOwner();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            Long chat = chatId.isBlank() ? null : Long.valueOf(chatId.strip());
            settings.save(enabled, chat, botToken, clearToken);
            return LOCATION + "?saved";
        } catch (IllegalArgumentException | IllegalStateException | DataAccessException exception) {
            // Never render rejected input or a database/transport exception containing credentials.
            return LOCATION + "?invalid";
        }
    }
}
