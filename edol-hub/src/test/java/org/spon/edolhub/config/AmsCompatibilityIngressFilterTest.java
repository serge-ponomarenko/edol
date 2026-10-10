package org.spon.edolhub.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.TenantContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AmsCompatibilityIngressFilterTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AmsCompatibilityIngressFilter filter = configuredFilter();

    @Test
    void matchesOnlyTrustedExactAmsRoutes() {
        MockHttpServletRequest allowed = request("GET", "/api/spools/find");
        allowed.addHeader("X-EDOL-AMS-INGRESS", "trusted");

        assertThat(filter.matches(allowed)).isTrue();
        assertThat(filter.matches(request("GET", "/api/spools"))).isFalse();
        assertThat(filter.matches(request("GET", "/s/1"))).isFalse();
        assertThat(filter.matches(request("POST", "/s/printer/spool/slot"))).isFalse();
    }

    @Test
    void rejectsAnUntrustedCopyOfAnAllowlistedRoute() {
        assertThat(filter.matches(request("GET", "/api/spools/find-by-id"))).isFalse();
    }

    @Test
    void recordsOnlyAcceptedLegacyIngressRequests() throws Exception {
        assertThat(meterRegistry.find("edol.hub.legacy_tenant_compatibility.uses").counter().count()).isZero();

        MockHttpServletRequest request = request("GET", "/api/spools/find");
        request.addHeader("X-EDOL-AMS-INGRESS", "trusted");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(meterRegistry.find("edol.hub.legacy_tenant_compatibility.uses").counter().count()).isEqualTo(1);
    }

    private AmsCompatibilityIngressFilter configuredFilter() {
        AmsCompatibilityIngressFilter result = new AmsCompatibilityIngressFilter(new TenantContext(), meterRegistry);
        ReflectionTestUtils.setField(result, "legacyTenantId", "00000000-0000-0000-0000-000000000001");
        ReflectionTestUtils.setField(result, "ingressToken", "trusted");
        return result;
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
