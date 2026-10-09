package org.spon.edolams.config;

import org.spon.edolams.service.AmsServiceAccessTokenProvider;
import org.spon.edolams.service.AmsTenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AmsTenantPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final AmsTenantContext tenantContext;
    private final ObjectProvider<AmsServiceAccessTokenProvider> tokenProvider;

    public AmsTenantPropagationInterceptor(
            AmsTenantContext tenantContext,
            ObjectProvider<AmsServiceAccessTokenProvider> tokenProvider
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
        AmsServiceAccessTokenProvider provider = tokenProvider.getIfAvailable();
        if (provider == null) {
            return execution.execute(request, body);
        }
        HttpHeaders headers = request.getHeaders();
        headers.remove("X-EDOL-Tenant-Id");
        headers.set("X-EDOL-Tenant-Id", tenantContext.currentTenantId().toString());
        headers.setBearerAuth(provider.accessTokenValue());
        return execution.execute(request, body);
    }
}
