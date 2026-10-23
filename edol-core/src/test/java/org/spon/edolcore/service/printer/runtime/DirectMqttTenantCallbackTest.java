package org.spon.edolcore.service.printer.runtime;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.telemetry.BambuTelemetryConsumer;
import org.spon.edolcore.service.printer.transport.BambuMqttConfiguration;
import org.spon.edolcore.service.printer.transport.BambuMqttConnection;
import org.spon.edolcore.service.tenant.CoreTenantContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectMqttTenantCallbackTest {
    private final CoreTenantContext tenants = new CoreTenantContext();
    private final CoreRuntimeTenantRegistry registry = new CoreRuntimeTenantRegistry(tenants);
    private final BambuTelemetryConsumer telemetry = mock(BambuTelemetryConsumer.class);
    private final PrinterConnectivityStateService connectivity = mock(PrinterConnectivityStateService.class);
    private final UUID printer = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @Test
    void scopesBothPahoCallbacksOnAFreshThreadAndClearsTheContextAfterwards() throws Exception {
        try (var ignored = tenants.open(owner)) {
            registry.bindCurrentTenant(printer);
        }
        var connection = connection(new SecureCoreRuntimeTenantExecutor(tenants, registry));
        byte[] payload = "{\"print\":{\"nozzle_temper\":24}}".getBytes(StandardCharsets.UTF_8);
        doAnswer(call -> {
            assertThat(tenants.getCurrentTenantId()).isEqualTo(owner);
            return null;
        }).when(telemetry).consume(printer, payload);
        doAnswer(call -> {
            assertThat(tenants.getCurrentTenantId()).isEqualTo(owner);
            return null;
        }).when(connectivity).setDisconnected(printer);
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertThat(tenants.hasCurrentTenant()).isFalse();
                connection.messageArrived("device/synthetic/report", new MqttMessage(payload));
                assertThat(tenants.hasCurrentTenant()).isFalse();
                connection.connectionLost(null);
                assertThat(tenants.hasCurrentTenant()).isFalse();
            }).get();
        }
        verify(telemetry).consume(printer, payload);
        verify(connectivity).setDisconnected(printer);
    }

    @Test
    void rejectsAnUnknownRuntimeInsteadOfGuessingATenant() {
        var connection = connection(new SecureCoreRuntimeTenantExecutor(tenants, registry));
        assertThatThrownBy(() -> connection.messageArrived("device/synthetic/report", new MqttMessage()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(telemetry, connectivity);
        assertThat(tenants.hasCurrentTenant()).isFalse();
    }

    @Test
    void homeCallbacksStillNeedNoTenantPrerequisites() {
        var connection = connection(new HomeCoreRuntimeTenantExecutor());
        byte[] payload = new byte[0];
        connection.messageArrived("device/synthetic/report", new MqttMessage(payload));
        verify(telemetry).consume(printer, payload);
        assertThat(tenants.hasCurrentTenant()).isFalse();
    }

    private BambuMqttConnection connection(CoreRuntimeTenantExecutor executor) {
        return new BambuMqttConnection(printer, new PrinterConnectionConfiguration(),
                new BambuMqttConfiguration("synthetic", 1), telemetry, connectivity, executor);
    }
}
