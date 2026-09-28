package org.spon.edolhub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.LegacyDefaultTenantCompatibilityScope;
import org.spon.edolhub.service.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LegacyDefaultTenantCompatibilityInterceptor implements HandlerInterceptor {

    private static final String SCOPE_ATTRIBUTE =
            LegacyDefaultTenantCompatibilityInterceptor.class.getName() + ".scope";

    private final LegacyDefaultTenantCompatibilityScope compatibilityScope;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantContext.TenantScope scope = compatibilityScope.open(
                request.getMethod() + " " + request.getRequestURI()
        );
        request.setAttribute(SCOPE_ATTRIBUTE, scope);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        Object scope = request.getAttribute(SCOPE_ATTRIBUTE);
        if (scope instanceof TenantContext.TenantScope tenantScope) {
            tenantScope.close();
        }
    }
}
