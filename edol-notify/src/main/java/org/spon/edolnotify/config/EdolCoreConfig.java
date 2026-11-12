package org.spon.edolnotify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class EdolCoreConfig {

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    private final NotifyTenantPropagationInterceptor tenantPropagationInterceptor;

    public EdolCoreConfig(NotifyTenantPropagationInterceptor tenantPropagationInterceptor) {
        this.tenantPropagationInterceptor = tenantPropagationInterceptor;
    }

    @Bean
    @Primary
    public RestClient edolCoreClient() {
        return RestClient.builder()
                .baseUrl(edolCoreUrl)
                .requestInterceptor(tenantPropagationInterceptor)
                .build();
    }

    @Bean
    public RestClient edolCoreSnapshotClient(@Qualifier("edolCoreClient") RestClient coreClient) {
        var http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return coreClient.mutate().requestFactory(factory).build();
    }


}
