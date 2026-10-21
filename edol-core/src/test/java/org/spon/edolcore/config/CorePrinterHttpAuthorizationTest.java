package org.spon.edolcore.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.controller.CameraController;
import org.spon.edolcore.controller.PrinterCommandController;
import org.spon.edolcore.controller.PrinterManagementController;
import org.spon.edolcore.controller.PrinterMediaController;
import org.spon.edolcore.controller.PrinterStateController;
import org.spon.edolcore.controller.dto.printer.PrinterMapper;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.camera.PrinterStatusImageService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityProvider;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.service.printer.management.exception.PrinterNotFoundException;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorePrinterHttpAuthorizationTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID PRINTER_A = UUID.randomUUID();
    private static final UUID PRINTER_B = UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void deniesForeignAndUnknownPrintersBeforeRuntimeMediaOrCommandAccess() throws Exception {
        start("secure-multi-tenant");
        PrinterManagementService management = context.getBean(PrinterManagementService.class);
        when(management.getPrinter(PRINTER_B)).thenReturn(printer(PRINTER_B, TENANT_B));
        String prefix = "/api/printers/" + PRINTER_B;
        for (String route : List.of("/state", "/camera/snapshot", "/camera/status-image",
                "/media/model/plate", "/media/model/top", "/connection")) {
            mvc.perform(authenticated(get(prefix + route))).andExpect(status().isNotFound());
        }
        for (String command : List.of("pause", "resume", "stop", "pushall", "fetchmetadata",
                "print-speed", "skip-objects", "spool-change")) {
            mvc.perform(authenticated(post(prefix + "/commands/" + command))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
        when(management.getPrinter(PRINTER_B)).thenThrow(new PrinterNotFoundException(PRINTER_B));
        mvc.perform(authenticated(get(prefix + "/state"))).andExpect(status().isNotFound());
        verifyNoInteractions(context.getBean(PrinterStateService.class),
                context.getBean(PrinterConnectivityProvider.class), context.getBean(CameraSnapshotStore.class),
                context.getBean(PrinterStatusImageService.class), context.getBean(ModelMetadataWorkflowService.class),
                context.getBean(PrinterCommandGateway.class));
        assertThat(context.getBean(CoreTenantContext.class).hasCurrentTenant()).isFalse();
    }

    @Test
    void allowsOwnedStateAndCommandsButDoesNotReturnConnectionSecrets() throws Exception {
        start("secure-multi-tenant");
        PrinterManagementService management = context.getBean(PrinterManagementService.class);
        when(management.getPrinter(PRINTER_A)).thenReturn(printer(PRINTER_A, TENANT_A));
        when(management.getConnection(PRINTER_A)).thenReturn(PrinterConnectionConfiguration.builder()
                .mqttHost("printer.example").accessCode("synthetic-secret").build());
        mvc.perform(authenticated(get("/api/printers/" + PRINTER_A + "/state")))
                .andExpect(status().isOk());
        mvc.perform(authenticated(post("/api/printers/" + PRINTER_A + "/commands/pause")))
                .andExpect(status().isOk());
        verify(context.getBean(PrinterCommandGateway.class)).pause(PRINTER_A);
        mvc.perform(authenticated(get("/api/printers/" + PRINTER_A + "/connection")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mqttHost").value("printer.example"))
                .andExpect(jsonPath("$.accessCode").doesNotExist());
    }

    @Test
    void filtersAggregateStatesToTheCurrentTenant() throws Exception {
        start("secure-multi-tenant");
        when(context.getBean(PrinterManagementService.class).getPrinters())
                .thenReturn(List.of(printer(PRINTER_A, TENANT_A)));
        when(context.getBean(PrinterStateService.class).getAllStates())
                .thenReturn(List.of(state(PRINTER_A), state(PRINTER_B)));
        mvc.perform(authenticated(get("/api/printers/state")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].printerId").value(PRINTER_A.toString()));
    }

    @Test
    void rejectsMissingAuthenticationAndTenantContext() throws Exception {
        start("secure-multi-tenant");
        mvc.perform(get("/api/printers/" + PRINTER_A + "/state")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/printers/" + PRINTER_A + "/state")
                        .header("Authorization", "Bearer synthetic-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(context.getBean(PrinterManagementService.class),
                context.getBean(PrinterStateService.class));
    }

    @Test
    void preservesAnonymousHomeStateAndCommandAccessWithoutTenantLookup() throws Exception {
        start("home");
        when(context.getBean(PrinterStateService.class).getAllStates())
                .thenReturn(List.of(state(PRINTER_A), state(PRINTER_B)));
        mvc.perform(get("/api/printers/state")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/printers/" + PRINTER_A + "/state")).andExpect(status().isOk());
        mvc.perform(get("/api/printers/" + PRINTER_A + "/camera/snapshot")).andExpect(status().isOk());
        mvc.perform(post("/api/printers/" + PRINTER_A + "/commands/pause")).andExpect(status().isOk());
        verify(context.getBean(PrinterCommandGateway.class)).pause(PRINTER_A);
        verifyNoInteractions(context.getBean(PrinterManagementService.class));
        assertThat(context.getBeansOfType(CorePrinterHttpAuthorizationConfiguration.class)).isEmpty();
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer synthetic-token")
                .header("X-EDOL-Tenant-Id", TENANT_A);
    }

    private void start(String mode) {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of("edol.deployment.mode=" + mode,
                "bot.webAdminName=synthetic-admin", "bot.webAdminPassword={noop}synthetic-password",
                "camera.store-snapshots=false", "camera.snapshot-dir=target/unused-http-test-snapshots")
                .applyTo(context);
        context.register(WebConfiguration.class);
        context.refresh();
        // Exclude Spring's startup lifecycle callback from HTTP access assertions.
        clearInvocations(context.getBean(CameraSnapshotStore.class));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    private static Printer printer(UUID id, UUID tenantId) {
        return Printer.builder().id(id).tenantId(tenantId).build();
    }

    private static PrinterState state(UUID id) {
        PrinterState state = new PrinterState();
        state.setPrinterId(id);
        return state;
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, CoreTenantContextFilter.class, CoreTenantContext.class,
            CorePrinterHttpAuthorizationConfiguration.class})
    static class WebConfiguration {
        @Bean
        DeploymentMode mode(org.springframework.core.env.Environment environment) {
            return "home".equals(environment.getRequiredProperty("edol.deployment.mode"))
                    ? DeploymentMode.HOME : DeploymentMode.SECURE_MULTI_TENANT;
        }

        @Bean
        JwtDecoder decoder() {
            JwtDecoder decoder = mock(JwtDecoder.class);
            Jwt jwt = Jwt.withTokenValue("synthetic-token").header("alg", "RS256")
                    .claim("azp", "edol-hub-service")
                    .claim("scope", "tenant.context core.state.read core.command.execute core.media.read core.printer.read")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
            when(decoder.decode(anyString())).thenReturn(jwt);
            return decoder;
        }

        @Bean PrinterManagementService management() { return mock(PrinterManagementService.class); }
        @Bean PrinterStateService states() { return mock(PrinterStateService.class); }
        @Bean PrinterConnectivityProvider connectivity() { return mock(PrinterConnectivityProvider.class); }
        @Bean CameraSnapshotStore snapshots() { return mock(CameraSnapshotStore.class); }
        @Bean PrinterStatusImageService statusImages() { return mock(PrinterStatusImageService.class); }
        @Bean ModelMetadataWorkflowService metadata() { return mock(ModelMetadataWorkflowService.class); }
        @Bean PrinterCommandGateway commands() { return mock(PrinterCommandGateway.class); }

        @Bean
        PrinterStateController stateController(PrinterConnectivityProvider connectivity, PrinterStateService states,
                PrinterManagementService management, DeploymentMode mode) {
            return new PrinterStateController(connectivity, states, management, mode);
        }

        @Bean
        CameraController cameraController(CameraSnapshotStore snapshots, PrinterStatusImageService images,
                ModelMetadataWorkflowService metadata) {
            return new CameraController(snapshots, images, metadata);
        }

        @Bean
        PrinterMediaController mediaController(PrinterStateService states, ModelMetadataWorkflowService metadata) {
            return new PrinterMediaController(states, metadata);
        }

        @Bean
        PrinterCommandController commandController(PrinterStateService states, PrinterCommandGateway commands,
                ModelMetadataWorkflowService metadata) {
            return new PrinterCommandController(new LogContextFactory(), states, commands, metadata);
        }

        @Bean
        PrinterManagementController managementController(PrinterManagementService management, CoreTenantContext tenant) {
            return new PrinterManagementController(management, new PrinterMapper(), null, null, null, tenant);
        }
    }
}
