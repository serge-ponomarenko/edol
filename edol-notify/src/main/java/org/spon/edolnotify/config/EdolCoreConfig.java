package org.spon.edolnotify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EdolCoreConfig {

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    @Bean
    public RestClient edloCoreRestClient() {
        return RestClient.builder()
                .baseUrl(edolCoreUrl)
                .build();
    }


}
