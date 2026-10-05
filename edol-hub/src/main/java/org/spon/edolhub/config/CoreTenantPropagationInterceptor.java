package org.spon.edolhub.config;

import org.spon.edolhub.service.CoreServiceAccessTokenProvider;
import org.spon.edolhub.service.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Adds authenticated service identity and the server-established tenant to
 * outbound Hub-to-Core requests. It never copies caller supplied headers.
 */
@Component
public class CoreTenantPropagationInterceptor implements ClientHttpRequestInterceptor {

    public static final String TENANT_HEADER = "X-EDOL-Tenant-Id";

    private final TenantContext tenantContext;
    private final ObjectProvider<CoreServiceAccessTokenProvider> tokenProvider;

    public CoreTenantPropagationInterceptor(
            TenantContext tenantContext,
            ObjectProvider<CoreServiceAccessTokenProvider> tokenProvider
    ) {
        this.tenantContext = tenantContext;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ClientHttpResponse intercept(
            org.springframework.http.HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        CoreServiceAccessTokenProvider provider = tokenProvider.getIfAvailable();
        if (provider == null) {
            return execution.execute(request, body);
        }

        HttpHeaders headers = request.getHeaders();
        headers.remove(TENANT_HEADER);
        headers.set(TENANT_HEADER, tenantContext.getCurrentTenantId().toString());
        headers.setBearerAuth(provider.accessTokenValue());
        return execution.execute(request, body);
    }
}
