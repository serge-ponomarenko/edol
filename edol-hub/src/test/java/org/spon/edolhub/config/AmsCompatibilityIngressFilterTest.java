package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.TenantContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AmsCompatibilityIngressFilterTest {

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

    private AmsCompatibilityIngressFilter configuredFilter() {
        AmsCompatibilityIngressFilter result = new AmsCompatibilityIngressFilter(new TenantContext());
        ReflectionTestUtils.setField(result, "legacyTenantId", "00000000-0000-0000-0000-000000000001");
        ReflectionTestUtils.setField(result, "ingressToken", "trusted");
        return result;
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}
