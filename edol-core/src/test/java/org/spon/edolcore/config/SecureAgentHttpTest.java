package org.spon.edolcore.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.controller.SecureAgentController;
import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.agent.pairing.AgentEnrollmentLimiter;
import org.spon.edolcore.service.agent.pairing.AgentPairingProperties;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.spon.edolcore.service.upload.ModelFileStorage;
import org.spon.edolcore.service.upload.SnapshotValidator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecureAgentHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private final UUID printer = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();

    @AfterEach void close() { if (context != null) context.close(); }

    private void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        String key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        TestPropertyValues.of("edol.deployment.mode=secure-multi-tenant", "edol-core.agent.enabled=true",
                "camera.store-snapshots=false", "camera.snapshot-dir=target/unused-agent-http-snapshots",
                "edol-core.agent.pairing-keys[1]=" + key, "edol-core.agent.credential-keys[1]=" + key).applyTo(context);
        context.register(WebConfig.class);
        context.refresh();
        clearInvocations(context.getBean(CameraSnapshotStore.class));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test
    void enrollmentRequiresTlsBoundedJsonAndIsNeverCached() throws Exception {
        start();
        var agents = context.getBean(AgentPairingService.class);
        when(agents.enroll("0123456789AB")).thenReturn(new AgentPairingService.Enrollment(UUID.randomUUID(), "synthetic"));
        mvc.perform(post("/api/agent/v1/enroll").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/agent/v1/enroll").secure(true).contentType("application/json")
                        .content("{\"pairingCode\":\"0123456789AB\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.secret").value("synthetic"));
        mvc.perform(post("/api/agent/v1/enroll").secure(true).contentType("application/json")
                        .content(" ".repeat(257))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/agent/v1/enroll").secure(true).contentType("application/json").content("not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void identityControlsSnapshotDestinationAndTenantScopeIsCleared() throws Exception {
        start();
        var agents = context.getBean(AgentPairingService.class);
        var identity = new AgentPairingService.Identity(UUID.randomUUID(), tenant, printer);
        when(agents.authenticate("synthetic")).thenReturn(identity);
        when(context.getBean(SnapshotValidator.class).read(any())).thenAnswer(invocation -> {
            assertThat(context.getBean(CoreTenantContext.class).getCurrentTenantId()).isEqualTo(tenant);
            return new byte[]{1, 2};
        });
        mvc.perform(post("/api/agent/v1/camera/snapshot").secure(true).contentType("image/jpeg").content("jpeg")
                        .header("Authorization", "EdolAgent synthetic"))
                .andExpect(status().isOk());
        verify(context.getBean(CameraSnapshotStore.class)).store(printer, new byte[]{1, 2});
        assertThat(context.getBean(CoreTenantContext.class).hasCurrentTenant()).isFalse();
        mvc.perform(post("/api/agent/v1/camera/snapshot").secure(true).contentType("image/jpeg")
                        .header("Authorization", "EdolAgent synthetic").header("X-EDOL-Tenant-Id", UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsJwtHeaderSpoofDuplicateAuthorizationAndRevokedCredentialsBeforeReadingUploads() throws Exception {
        start();
        for (String authorization : new String[]{"Bearer service-token", "Basic synthetic"}) {
            mvc.perform(post("/api/agent/v1/upload").secure(true).param("fileName", "part.3mf")
                            .header("Authorization", authorization).contentType("application/octet-stream"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/agent/v1/upload").secure(true).header("X-Agent-Id", "spoof"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/agent/v1/upload").secure(true)
                        .header("Authorization", "EdolAgent one", "EdolAgent two"))
                .andExpect(status().isUnauthorized());
        when(context.getBean(AgentPairingService.class).authenticate("revoked"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/agent/v1/upload").secure(true).header("Authorization", "EdolAgent revoked"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(ModelFileStorage.class), context.getBean(SnapshotValidator.class),
                context.getBean(CameraSnapshotStore.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc @EnableWebSecurity
    @Import({AgentPairingConfiguration.class, SecureAgentController.class, CoreTenantContext.class})
    static class WebConfig {
        @Bean @Primary AgentPairingService agents() { return mock(AgentPairingService.class); }
        @Bean org.springframework.jdbc.core.JdbcTemplate jdbc() { return mock(org.springframework.jdbc.core.JdbcTemplate.class); }
        @Bean ModelFileStorage files() { return mock(ModelFileStorage.class); }
        @Bean SnapshotValidator images() { return mock(SnapshotValidator.class); }
        @Bean CameraSnapshotStore snapshots() { return mock(CameraSnapshotStore.class); }
        @Bean com.fasterxml.jackson.databind.ObjectMapper mapper() { return new com.fasterxml.jackson.databind.ObjectMapper(); }
    }
}
