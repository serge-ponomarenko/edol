package org.spon.edolams.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EdolCoreConfig {

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    private final AmsTenantPropagationInterceptor tenantPropagationInterceptor;

    public EdolCoreConfig(AmsTenantPropagationInterceptor tenantPropagationInterceptor) {
        this.tenantPropagationInterceptor = tenantPropagationInterceptor;
    }

    @Bean
    public RestClient edolCoreRestClient() {
        return RestClient.builder()
                .baseUrl(edolCoreUrl)
                .requestInterceptor(tenantPropagationInterceptor)
                .build();
    }

}
