package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.config.AmsTenantPropagationInterceptor;
import org.spon.edolhub.config.CoreTenantPropagationInterceptor;
import org.spon.edolhub.config.EdolAmsConfig;
import org.spon.edolhub.config.EdolCoreConfig;
import org.spon.edolhub.service.spool.AllocationPreviewRuntimeCacheService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CoreRestClientInjectionContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RestClientConfiguration.class)
            .withPropertyValues(
                    "edol.deployment.mode=secure-multi-tenant",
                    "edol-core.url=http://core",
                    "edol-ams.url=http://ams"
            );

    @Test
    void injectsTheCoreClientWhenCoreAndAmsClientsAreBothPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            RestClient coreClient = context.getBean("edolCoreRestClient", RestClient.class);
            RestClient amsClient = context.getBean("edolAmsRestClient", RestClient.class);

            assertThat(ReflectionTestUtils.getField(context.getBean(PrinterService.class), "edolCoreClient"))
                    .isSameAs(coreClient);
            assertThat(ReflectionTestUtils.getField(context.getBean(PrinterManagementService.class), "edolCoreClient"))
                    .isSameAs(coreClient);
            assertThat(ReflectionTestUtils.getField(context.getBean(PrinterDashboardStateService.class), "edolCoreClient"))
                    .isSameAs(coreClient);
            assertThat(ReflectionTestUtils.getField(context.getBean(AmsTerminalManagementClient.class), "amsClient"))
                    .isSameAs(amsClient);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            EdolCoreConfig.class,
            EdolAmsConfig.class,
            PrinterService.class,
            PrinterManagementService.class,
            PrinterDashboardStateService.class,
            AmsTerminalManagementClient.class
    })
    static class RestClientConfiguration {

        @Bean
        CoreTenantPropagationInterceptor coreTenantPropagationInterceptor() {
            return mock(CoreTenantPropagationInterceptor.class);
        }

        @Bean
        AmsTenantPropagationInterceptor amsTenantPropagationInterceptor() {
            return mock(AmsTenantPropagationInterceptor.class);
        }

        @Bean
        PrintRuntimeStateService runtimeStateService() {
            return mock(PrintRuntimeStateService.class);
        }

        @Bean
        AllocationPreviewRuntimeCacheService runtimeCacheService() {
            return mock(AllocationPreviewRuntimeCacheService.class);
        }
    }
}
