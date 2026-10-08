package org.spon.edolcore.config;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.printer.runtime.DisabledPrinterRuntimeLifecycleService;
import org.spon.edolcore.service.printer.runtime.PrinterRuntimeBootstrap;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CoreRuntimeIsolationConfigurationTest {

    @Test
    void disablesPahoTransportButRetainsInProcessChannels() {
        new ApplicationContextRunner()
                .withUserConfiguration(MqttSubscribeConfig.class, MqttPublishConfig.class)
                .withPropertyValues("edol-core.runtime.mqtt-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("mqttClientFactory");
                    assertThat(context).doesNotHaveBean("mqttInboundClientFactory");
                    assertThat(context).doesNotHaveBean("mqttInbound");
                    assertThat(context).doesNotHaveBean("mqttOutbound");
                    assertThat(context).doesNotHaveBean("coreEventsOutbound");
                    assertThat(context).hasBean("mqttInboundChannel");
                    assertThat(context).hasBean("mqttOutboundChannel");
                    assertThat(context).hasBean("coreEventsOutboundChannel");
                });
    }

    @Test
    void enablesOnlyTheIntegrationPublisherForTheStage6SmokeBoundary() {
        new ApplicationContextRunner()
                .withUserConfiguration(MqttSubscribeConfig.class, MqttPublishConfig.class)
                .withPropertyValues(
                        "mqttServer.url=tcp://127.0.0.1:1883",
                        "edol-core.runtime.mqtt-enabled=false",
                        "edol-core.runtime.integration-mqtt-enabled=true"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean("mqttClientFactory");
                    assertThat(context).doesNotHaveBean("mqttInboundClientFactory");
                    assertThat(context).doesNotHaveBean("mqttInbound");
                    assertThat(context).doesNotHaveBean("mqttOutbound");
                    assertThat(context).hasBean("coreEventsOutbound");
                });
    }

    @Test
    void replacesDeviceLifecycleAndSkipsRuntimeBootstrap() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        DisabledPrinterRuntimeLifecycleService.class,
                        PrinterRuntimeBootstrap.class
                )
                .withPropertyValues("edol-core.runtime.printer-runtime-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(DisabledPrinterRuntimeLifecycleService.class);
                    assertThat(context).doesNotHaveBean(PrinterRuntimeBootstrap.class);
                });
    }
}
