package org.spon.edolcore.service.printer.transport;

import lombok.RequiredArgsConstructor;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.printer.connectivity.PrinterConnectivityStateService;
import org.spon.edolcore.service.printer.telemetry.BambuTelemetryConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BambuMqttConnectionManager {

    private final BambuTelemetryConsumer telemetryConsumer;
    private final PrinterConnectivityStateService connectivityStateService;
    private final PrinterConnectionConfigurationRepository connectionConfigurationRepository;

    private final Map<UUID, BambuMqttConnection> connections =
            new ConcurrentHashMap<>();

    @Value("${bambu.client-id}")
    private String clientId;

    @Value("${bambu.connection-timeout}")
    private Integer connectionTimeout;

    private BambuMqttConnection getOrCreate(UUID printerId) {
        return connections.computeIfAbsent(
                printerId,
                id -> new BambuMqttConnection(
                        id,
                        connectionConfigurationRepository
                                .findByPrinterId(id)
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "Missing connection configuration for printer " + id
                                        )),
                        new BambuMqttConfiguration(
                                clientId,
                                connectionTimeout
                        ),
                        telemetryConsumer,
                        connectivityStateService
                )
        );
    }

    public BambuMqttConnection getConnection(UUID printerId) {
        return getOrCreate(printerId);
    }

    public void connect(UUID printerId) {
        getOrCreate(printerId).connect();
    }

    public void disconnect(UUID printerId) {
        BambuMqttConnection connection = connections.get(printerId);

        if (connection != null) {
            connection.disconnect();
        }
    }

    public boolean isConnected(UUID printerId) {
        BambuMqttConnection connection =
                connections.get(printerId);

        return connection != null
                && connection.isConnected();
    }

    public void remove(UUID printerId) {
        BambuMqttConnection connection = connections.remove(printerId);

        if (connection != null) {
            connection.disconnect();
        }
    }

    public Set<UUID> getConnectedPrinters() {
        return connections.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isConnected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }


}
