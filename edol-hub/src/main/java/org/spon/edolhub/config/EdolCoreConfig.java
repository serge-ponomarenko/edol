package org.spon.edolhub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class EdolCoreConfig {

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    private final CoreTenantPropagationInterceptor coreTenantPropagationInterceptor;

    public EdolCoreConfig(CoreTenantPropagationInterceptor coreTenantPropagationInterceptor) {
        this.coreTenantPropagationInterceptor = coreTenantPropagationInterceptor;
    }

    @Bean
    public RestClient edloCoreRestClient() {
        return RestClient.builder()
                .baseUrl(edolCoreUrl)
                .requestInterceptor(coreTenantPropagationInterceptor)
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(coreTenantPropagationInterceptor);
        return restTemplate;
    }

}
