package org.spon.edolams.service;

import org.junit.jupiter.api.Test;
import org.spon.edolams.repository.AmsTerminalRepository;
import org.spon.edolams.repository.TerminalPairingRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TerminalPairingManagementServiceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("edol.deployment.mode=secure-multi-tenant")
            .withUserConfiguration(PairingServiceConfiguration.class);

    @Test
    void createsPairingManagementServiceWithProductionConstructor() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(TerminalPairingManagementService.class)
        );
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = TerminalPairingManagementService.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = TerminalPairingManagementService.class
            )
    )
    static class PairingServiceConfiguration {

        @Bean
        AmsTerminalRepository terminalRepository() {
            return mock(AmsTerminalRepository.class);
        }

        @Bean
        TerminalPairingRepository pairingRepository() {
            return mock(TerminalPairingRepository.class);
        }

        @Bean
        AmsTenantContext tenantContext() {
            return mock(AmsTenantContext.class);
        }

        @Bean
        TerminalCredentialCodec credentialCodec() {
            return mock(TerminalCredentialCodec.class);
        }
    }
}
