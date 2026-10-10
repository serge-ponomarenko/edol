package org.spon.edolhub.config;

import org.spon.edolhub.service.CoreServiceAccessTokenProvider;
import org.spon.edolhub.service.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;

/** Adds Hub's service identity and the server-established tenant to AMS management calls. */
@Component
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class AmsTenantPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final TenantContext tenantContext;
    private final CoreServiceAccessTokenProvider tokenProvider;

    public AmsTenantPropagationInterceptor(TenantContext tenantContext, CoreServiceAccessTokenProvider tokenProvider) {
        this.tenantContext = tenantContext;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ClientHttpResponse intercept(
            org.springframework.http.HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        HttpHeaders headers = request.getHeaders();
        headers.remove(CoreTenantPropagationInterceptor.TENANT_HEADER);
        headers.set(CoreTenantPropagationInterceptor.TENANT_HEADER, tenantContext.getCurrentTenantId().toString());
        headers.setBearerAuth(tokenProvider.accessTokenValue());
        return execution.execute(request, body);
    }
}
