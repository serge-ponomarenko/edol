package org.spon.edolcore.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import org.spon.edolcore.service.printer.management.exception.PrinterNotFoundException;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.UUID;

/** Checks persisted ownership before HTTP handlers can access runtime state or issue commands. */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class CorePrinterHttpAuthorizationConfiguration implements WebMvcConfigurer {

    private final PrinterManagementService printerManagementService;
    private final CoreTenantContext tenantContext;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (variables instanceof Map<?, ?> pathVariables
                        && pathVariables.get("printerId") instanceof String value) {
                    if (!tenantContext.hasCurrentTenant()) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                    }
                    UUID printerId;
                    try {
                        printerId = UUID.fromString(value);
                    } catch (IllegalArgumentException exception) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid printer identity");
                    }
                    UUID tenantId = tenantContext.getCurrentTenantId();
                    if (!tenantId.equals(printerManagementService.getPrinter(printerId).getTenantId())) {
                        throw new PrinterNotFoundException(printerId);
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/printers/**");
    }
}
