package org.spon.edolams.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EdolDashboardConfig {

    @Value("${edol-dashboard.url}")
    private String edolDashboardUrl;

    @Bean
    public RestClient edolDashboardRestClient() {
        return RestClient.builder()
                .baseUrl(edolDashboardUrl)
                .build();
    }

}
