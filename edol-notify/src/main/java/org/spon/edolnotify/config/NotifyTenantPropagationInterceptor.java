package org.spon.edolnotify.config;

import org.spon.edolnotify.service.NotifyServiceAccessTokenProvider;
import org.spon.edolnotify.service.NotifyTenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NotifyTenantPropagationInterceptor implements ClientHttpRequestInterceptor {

    static final String TENANT_HEADER = "X-EDOL-Tenant-Id";

    private final NotifyTenantContext tenantContext;
    private final ObjectProvider<NotifyServiceAccessTokenProvider> tokenProvider;

    public NotifyTenantPropagationInterceptor(
            NotifyTenantContext tenantContext,
            ObjectProvider<NotifyServiceAccessTokenProvider> tokenProvider
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
        NotifyServiceAccessTokenProvider provider = tokenProvider.getIfAvailable();
        if (provider == null) {
            return execution.execute(request, body);
        }
        HttpHeaders headers = request.getHeaders();
        headers.remove(TENANT_HEADER);
        headers.set(TENANT_HEADER, tenantContext.currentTenantId().toString());
        headers.setBearerAuth(provider.accessTokenValue());
        return execution.execute(request, body);
    }
}
