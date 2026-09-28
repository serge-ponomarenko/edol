package org.spon.edolhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class HubTenantTaskExecutorConfiguration {

    @Bean(name = "hubTenantTaskExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor hubTenantTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("hub-tenant-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.initialize();
        return executor;
    }
}
