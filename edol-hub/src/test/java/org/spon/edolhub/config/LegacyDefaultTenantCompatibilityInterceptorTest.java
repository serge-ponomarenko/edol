package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.LegacyDefaultTenantCompatibilityScope;
import org.spon.edolhub.service.TenantContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyDefaultTenantCompatibilityInterceptorTest {

    @Test
    void opensAndClosesTheScopeForAnAllowlistedHttpRequest() {
        LegacyDefaultTenantCompatibilityScope compatibilityScope = mock(LegacyDefaultTenantCompatibilityScope.class);
        TenantContext.TenantScope tenantScope = mock(TenantContext.TenantScope.class);
        when(compatibilityScope.open("GET /printers")).thenReturn(tenantScope);
        LegacyDefaultTenantCompatibilityInterceptor interceptor =
                new LegacyDefaultTenantCompatibilityInterceptor(compatibilityScope);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/printers");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        verify(compatibilityScope).open("GET /printers");
        verify(tenantScope).close();
    }
}
