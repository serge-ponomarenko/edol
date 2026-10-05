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
                    assertThat(context).hasBean("mqttInboundChannel");
                    assertThat(context).hasBean("mqttOutboundChannel");
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
