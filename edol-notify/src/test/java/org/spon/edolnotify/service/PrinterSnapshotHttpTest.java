package org.spon.edolnotify.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spon.edolnotify.config.NotifyTenantPropagationInterceptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrinterSnapshotHttpTest {
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID PRINTER = UUID.randomUUID();
    private static final String URL = "http://core.invalid/api/printers/" + PRINTER + "/camera/status-image/content";
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};
    private final NotifyTenantContext tenants = new NotifyTenantContext();
    private MockRestServiceServer server;
    private PrinterService service;

    @BeforeEach
    void configureAuthenticatedHttpClient() {
        var tokens = mock(NotifyServiceAccessTokenProvider.class);
        when(tokens.accessTokenValue()).thenReturn("synthetic-service-token");
        var beans = new StaticListableBeanFactory();
        beans.addBean("tokens", tokens);
        var interceptor = new NotifyTenantPropagationInterceptor(tenants,
                beans.getBeanProvider(NotifyServiceAccessTokenProvider.class));
        var builder = RestClient.builder().baseUrl("http://core.invalid").requestInterceptor(interceptor);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        service = new PrinterService(client, client);
    }

    @Test
    void carriesServiceTokenAndCurrentTenantAndNeverReusesAnotherTenantsSnapshot() {
        server.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-service-token"))
                .andExpect(header("X-EDOL-Tenant-Id", TENANT_A.toString()))
                .andExpect(header(HttpHeaders.ACCEPT, "image/jpeg"))
                .andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        server.expect(requestTo(URL))
                .andExpect(header("X-EDOL-Tenant-Id", TENANT_B.toString()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        try (var ignored = tenants.open(TENANT_A)) {
            assertThat(service.getStatusImage(PRINTER)).containsExactly(JPEG);
        }
        try (var ignored = tenants.open(TENANT_B)) {
            assertThat(service.getStatusImage(PRINTER)).isNull();
        }
        server.verify();
    }

    @Test
    void missingTenantFailsBeforeAnyHttpRequest() {
        assertThatThrownBy(() -> service.getStatusImage(PRINTER))
                .isInstanceOf(IllegalStateException.class).hasMessage("Notify tenant context is required");
        server.verify();
    }

    @Test
    void emptyRejectedAndInvalidImagesAreOptional() {
        server.expect(requestTo(URL)).andRespond(withSuccess(new byte[0], MediaType.IMAGE_JPEG));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess(JPEG, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(new byte[]{1, 2, 3, 4}, MediaType.IMAGE_JPEG));
        try (var ignored = tenants.open(TENANT_A)) {
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThat(service.getStatusImage(PRINTER)).isNull();
            }
        }
        server.verify();
    }

    @Test
    void rejectsOversizedBodiesAndFallsBackOnTimeout() {
        byte[] oversized = new byte[PrinterService.MAX_SNAPSHOT_BYTES + 1];
        oversized[0] = (byte) 0xff;
        oversized[1] = (byte) 0xd8;
        oversized[oversized.length - 2] = (byte) 0xff;
        oversized[oversized.length - 1] = (byte) 0xd9;
        server.expect(requestTo(URL)).andRespond(withSuccess(oversized, MediaType.IMAGE_JPEG));
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("synthetic timeout")));
        try (var ignored = tenants.open(TENANT_A)) {
            assertThat(service.getStatusImage(PRINTER)).isNull();
            assertThat(service.getStatusImage(PRINTER)).isNull();
        }
        server.verify();
    }
}
