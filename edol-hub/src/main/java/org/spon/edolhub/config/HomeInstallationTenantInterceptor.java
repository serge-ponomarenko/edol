package org.spon.edolhub.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.TenantContext;
import org.spon.edolhub.service.TenantScopeProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "home")
public class HomeInstallationTenantInterceptor implements HandlerInterceptor {

    private static final String SCOPE_ATTRIBUTE =
            HomeInstallationTenantInterceptor.class.getName() + ".scope";

    private final TenantScopeProvider tenantScopeProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantContext.TenantScope scope = tenantScopeProvider.open(
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
