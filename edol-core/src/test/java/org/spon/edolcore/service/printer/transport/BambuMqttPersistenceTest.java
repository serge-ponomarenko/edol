package org.spon.edolcore.service.printer.transport;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.runtime.CoreRuntimeTenantExecutor;
import org.spon.edolcore.service.printer.telemetry.BambuTelemetryConsumer;
import org.spon.edolcore.util.SslUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class BambuMqttPersistenceTest {
    @TempDir Path temporary;

    @Test
    void implicitPahoPersistenceFailsBeforeNetworkWhenWorkingDirectoryCannotHoldFiles() throws Exception {
        String original = System.getProperty("user.dir");
        Path blocked = Files.createFile(temporary.resolve("not-a-directory"));
        System.setProperty("user.dir", blocked.toString());
        try {
            assertThatThrownBy(() -> {
                try (var ignored = new MqttClient("ssl://printer.invalid:8883", "implicit-test")) {
                    // Construction alone opens persistence; no network operation is invoked.
                }
            }).isInstanceOf(MqttPersistenceException.class);
        } finally {
            System.setProperty("user.dir", original);
        }
    }

    @Test
    void productionClientReachesTlsSetupWithoutWritingToItsWorkingDirectory() throws Exception {
        String original = System.getProperty("user.dir");
        Path blocked = Files.createFile(temporary.resolve("not-a-directory"));
        var configuration = PrinterConnectionConfiguration.builder()
                .mqttHost("printer.invalid").mqttPort(8883).accessCode("synthetic").build();
        var connectivity = mock(PrinterConnectivityStateService.class);
        var connection = new BambuMqttConnection(UUID.randomUUID(), configuration,
                new BambuMqttConfiguration("production-test", 1), mock(BambuTelemetryConsumer.class),
                connectivity, mock(CoreRuntimeTenantExecutor.class));
        System.setProperty("user.dir", blocked.toString());
        try (var tls = mockStatic(SslUtil.class)) {
            // Stop before client.connect(): this regression must never contact a printer.
            tls.when(SslUtil::createTrustAllSocketFactory).thenThrow(new IllegalStateException("stop-before-network"));
            connection.connect();
            assertThat(connection.getClient()).isNotNull();
            tls.verify(SslUtil::createTrustAllSocketFactory);
            assertThat(connection.isConnected()).isFalse();
            verifyNoInteractions(connectivity);
            assertThat(Files.readAllBytes(blocked)).isEmpty();
        } finally {
            connection.disconnect();
            System.setProperty("user.dir", original);
        }
    }
}
