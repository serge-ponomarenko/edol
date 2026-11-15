package org.spon.edolhub.controller;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.CoreAgentManagementClient;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.spon.edolhub.service.PrinterManagementService;
import org.spon.edolhub.service.PrinterCatalogSyncService;
import org.spon.edol.deployment.DeploymentMode;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentManagementPageTest {
    @Test
    void rendersOwnerNavigationOnConnectionSettings() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant", "edol-hub.agent.enabled=true").applyTo(context);
            context.register(WebConfig.class);
            context.refresh();
            UUID printer = UUID.randomUUID();
            var management = context.getBean(PrinterManagementService.class);
            when(management.getPrinter(printer)).thenReturn(new org.spon.edolhub.model.dto.CorePrinterDto(
                    printer, "P1", "Printer", null, null, "BAMBU_P1S", "AGENT", "LEGACY", false));
            when(management.getConnection(printer)).thenReturn(new org.spon.edolhub.model.dto.CorePrinterConnectionDto(
                    null, null, null, null, null, null));
            MockMvcBuilders.webAppContextSetup(context).build().perform(get("/printers/" + printer + "/settings/connection")
                            .flashAttr("printers", java.util.List.of()).flashAttr("currentPath", "/printers/" + printer + "/edit"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Manage printer agent")))
                    .andExpect(content().string(containsString("/printers/" + printer + "/agent")))
                    .andExpect(content().string(containsString("Enable this AGENT printer")))
                    .andExpect(content().string(containsString("const secureDeployment = true")));
        }
    }
    @Test
    void rendersGeneralSettingsWithoutConnectionInputsAndKeepsMaintenanceInTabs() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant").applyTo(context);
            context.register(WebConfig.class);
            context.refresh();
            UUID printer = UUID.randomUUID();
            var management = context.getBean(PrinterManagementService.class);
            when(management.getPrinter(printer)).thenReturn(new org.spon.edolhub.model.dto.CorePrinterDto(
                    printer, "P1", "My printer", null, null, "BAMBU_P1S", "DIRECT", "LEGACY", true));
            when(management.getConnection(printer)).thenReturn(new org.spon.edolhub.model.dto.CorePrinterConnectionDto(
                    "printer.local", 8883, "printer.local", 990, "/", null));
            MockMvcBuilders.webAppContextSetup(context).build().perform(get("/printers/" + printer + "/edit")
                            .flashAttr("printers", java.util.List.of()).flashAttr("currentPath", "/printers/" + printer + "/edit"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Printer settings")))
                    .andExpect(content().string(containsString("My printer")))
                    .andExpect(content().string(containsString("/settings/general")))
                    .andExpect(content().string(containsString("/maintenance/config")))
                    .andExpect(content().string(not(containsString("id=\"mqttHost\""))))
                    .andExpect(content().string(not(containsString("Inventory Configuration"))))
                    .andExpect(content().string(not(containsString("Maintenance Definitions"))));
        }
    }

    @Test
    void rendersTheActualTemplateAndDoesNotRedisplayThePairingCodeOnGet() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant", "edol-hub.agent.enabled=true").applyTo(context);
            context.register(WebConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            UUID printer = UUID.randomUUID();
            when(context.getBean(CoreAgentManagementClient.class).pair(printer, false))
                    .thenReturn(new CoreAgentManagementClient.Pairing(UUID.randomUUID(), "0123456789AB", Instant.now()));
            String path = "/printers/" + printer + "/agent";
            mvc.perform(post(path + "/pairings")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("0123456789AB")))
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("0123456789AB"))))
                    .andExpect(content().string(containsString("Not paired")));
        }
    }

    @Test
    void rendersWorkspaceSettingsAndMaintenanceScheduleInTheirOwnContexts() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant").applyTo(context);
            context.register(WebConfig.class);
            context.refresh();
            when(context.getBean(org.spon.edolhub.service.TenantTelegramSettingsService.class).status())
                    .thenReturn(new org.spon.edolhub.service.TenantTelegramSettingsService.Status(false, null, false, true));
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/settings").flashAttr("printers", java.util.List.of()).flashAttr("currentPath", "/settings"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Settings for this workspace")))
                    .andExpect(content().string(containsString("action=\"/settings/telegram\"")))
                    .andExpect(content().string(containsString("Saved tokens are never displayed")))
                    .andExpect(header().string("Cache-Control", "no-store"));
            UUID printer = UUID.randomUUID();
            mvc.perform(get("/printers/" + printer + "/maintenance/config")
                            .flashAttr("printers", java.util.List.of()).flashAttr("currentPath", "/printers/" + printer + "/maintenance/config"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Maintenance schedule")))
                    .andExpect(content().string(containsString("Add maintenance task")))
                    .andExpect(content().string(containsString("/printers/" + printer + "/settings/connection")));
        }
    }

    @Test
    void overviewLinksDirectlyToSelectedPrinterSettings() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebConfig.class);
            context.refresh();
            UUID printer = UUID.randomUUID();
            MockMvcBuilders.webAppContextSetup(context).build().perform(get("/printers/" + printer)
                            .flashAttr("currentPath", "/printers/" + printer))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("aria-label=\"Printer settings\"")))
                    .andExpect(content().string(containsString("href=\"/printers/" + printer + "/edit\"")));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({AgentManagementController.class, PrinterManagementController.class, TelegramSettingsController.class, MaintenanceConfigController.class, DashboardController.class})
    static class WebConfig {
        @Bean org.spon.edolhub.service.PrinterService printerService() { return mock(org.spon.edolhub.service.PrinterService.class); }
        @Bean org.spon.edolhub.repository.MaintenanceDefinitionRepository definitions() { return mock(org.spon.edolhub.repository.MaintenanceDefinitionRepository.class); }
        @Bean org.spon.edolhub.service.PrinterStatsService stats() { return mock(org.spon.edolhub.service.PrinterStatsService.class); }
        @Bean org.spon.edolhub.service.TenantTelegramSettingsService telegram() { return mock(org.spon.edolhub.service.TenantTelegramSettingsService.class); }
        @Bean CoreAgentManagementClient agents() { return mock(CoreAgentManagementClient.class); }
        @Bean PrinterAccessService printers() { return mock(PrinterAccessService.class); }
        @Bean TenantOwnerAuthorizationService owners() { return mock(TenantOwnerAuthorizationService.class); }
        @Bean PrinterManagementService management() { return mock(PrinterManagementService.class); }
        @Bean PrinterCatalogSyncService sync() { return mock(PrinterCatalogSyncService.class); }
        @Bean DeploymentMode deploymentMode() { return DeploymentMode.SECURE_MULTI_TENANT; }
        @Bean SpringResourceTemplateResolver templateResolver() {
            var resolver = new SpringResourceTemplateResolver();
            resolver.setPrefix("classpath:/templates/");
            resolver.setSuffix(".html");
            return resolver;
        }
        @Bean SpringTemplateEngine templateEngine(SpringResourceTemplateResolver resolver) {
            var engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            return engine;
        }
        @Bean ThymeleafViewResolver viewResolver(SpringTemplateEngine engine) {
            var resolver = new ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            return resolver;
        }
    }
}
