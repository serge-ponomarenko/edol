package org.spon.edolams.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EdolHubConfig {

    @Value("${edol-hub.url}")
    private String edolHubUrl;

    private final AmsTenantPropagationInterceptor tenantPropagationInterceptor;

    public EdolHubConfig(AmsTenantPropagationInterceptor tenantPropagationInterceptor) {
        this.tenantPropagationInterceptor = tenantPropagationInterceptor;
    }

    @Bean
    public RestClient edolHubRestClient() {
        return RestClient.builder()
                .baseUrl(edolHubUrl)
                .requestInterceptor(tenantPropagationInterceptor)
                .build();
    }

}
