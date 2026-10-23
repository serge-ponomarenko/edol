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
    void rendersOwnerNavigationFromTheActualPrinterEditPage() throws Exception {
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
            MockMvcBuilders.webAppContextSetup(context).build().perform(get("/printers/" + printer + "/edit")
                            .flashAttr("printers", java.util.List.of()).flashAttr("currentPath", "/printers/" + printer + "/edit"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Manage printer agent")))
                    .andExpect(content().string(containsString("/printers/" + printer + "/agent")))
                    .andExpect(content().string(containsString("Enable this AGENT printer")))
                    .andExpect(content().string(containsString("const secureDeployment = true")));
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

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({AgentManagementController.class, PrinterManagementController.class})
    static class WebConfig {
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
