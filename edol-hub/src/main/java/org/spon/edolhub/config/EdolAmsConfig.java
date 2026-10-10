package org.spon.edolhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class EdolAmsConfig {

    private final AmsTenantPropagationInterceptor tenantPropagationInterceptor;

    public EdolAmsConfig(AmsTenantPropagationInterceptor tenantPropagationInterceptor) {
        this.tenantPropagationInterceptor = tenantPropagationInterceptor;
    }

    @Bean
    RestClient edolAmsRestClient(@Value("${edol-ams.url}") String edolAmsUrl) {
        return RestClient.builder()
                .baseUrl(edolAmsUrl)
                .requestInterceptor(tenantPropagationInterceptor)
                .build();
    }
}
