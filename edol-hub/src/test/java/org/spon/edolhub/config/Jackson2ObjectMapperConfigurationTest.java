package org.spon.edolhub.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.spon.edolhub.event.SecureMqttEventListener;
import org.spon.edolhub.service.CoreMqttEventReceiptService;
import org.spon.edolhub.service.PrintJobService;
import org.spon.edolhub.service.PrinterService;
import org.spon.edolhub.service.TenantContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SuppressWarnings("removal")
class Jackson2ObjectMapperConfigurationTest {

    @Test
    void createsSecureMqttEventListenerWithTheJackson2ObjectMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(Jackson2AutoConfiguration.class))
                .withUserConfiguration(SecureMqttEventListenerConfiguration.class)
                .withPropertyValues(
                        "edol.deployment.mode=secure-multi-tenant",
                        "edol-hub.runtime.mqtt-enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(SecureMqttEventListener.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class SecureMqttEventListenerConfiguration {

        @Bean
        SecureMqttEventListener secureMqttEventListener(
                PrinterService printerService,
                PrintJobService printJobService,
                TenantContext tenantContext,
                CoreMqttEventReceiptService receiptService,
                ObjectMapper objectMapper
        ) {
            return new SecureMqttEventListener(
                    printerService,
                    printJobService,
                    tenantContext,
                    receiptService,
                    objectMapper
            );
        }

        @Bean
        PrinterService printerService() {
            return mock(PrinterService.class);
        }

        @Bean
        PrintJobService printJobService() {
            return mock(PrintJobService.class);
        }

        @Bean
        TenantContext tenantContext() {
            return mock(TenantContext.class);
        }

        @Bean
        CoreMqttEventReceiptService receiptService() {
            return mock(CoreMqttEventReceiptService.class);
        }
    }
}
