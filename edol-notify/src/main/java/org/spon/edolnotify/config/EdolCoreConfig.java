package org.spon.edolnotify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EdolCoreConfig {

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    private final NotifyTenantPropagationInterceptor tenantPropagationInterceptor;

    public EdolCoreConfig(NotifyTenantPropagationInterceptor tenantPropagationInterceptor) {
        this.tenantPropagationInterceptor = tenantPropagationInterceptor;
    }

    @Bean
    public RestClient edolCoreClient() {
        return RestClient.builder()
                .baseUrl(edolCoreUrl)
                .requestInterceptor(tenantPropagationInterceptor)
                .build();
    }


}
