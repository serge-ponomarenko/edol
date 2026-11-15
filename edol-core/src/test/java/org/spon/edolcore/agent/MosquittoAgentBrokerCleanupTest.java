package org.spon.edolcore.agent;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.agent.mqtt.AgentMqttProperties;
import org.spon.edolcore.service.agent.mqtt.MosquittoAgentBroker;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MosquittoAgentBrokerCleanupTest {
    @Test
    void closesClientEvenWhenConnectionAndDisconnectFail() throws Exception {
        AgentMqttProperties properties = new AgentMqttProperties(true, "control-test", "synthetic-test-password");
        MosquittoAgentBroker broker = new MosquittoAgentBroker("ssl://broker.test:8883", properties);
        try (var clients = mockConstruction(MqttClient.class, (client, context) -> {
            doThrow(new MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR))
                    .when(client).connect(any(MqttConnectOptions.class));
            when(client.isConnected()).thenReturn(true);
            doThrow(new MqttException(MqttException.REASON_CODE_CONNECTION_LOST)).when(client).disconnect();
        })) {
            UUID agentId = UUID.randomUUID();
            assertThatThrownBy(() -> broker.disable(agentId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Agent broker operation could not be confirmed")
                    .hasNoCause();

            MqttClient client = clients.constructed().getFirst();
            verify(client).disconnect();
            verify(client).close();
        }
    }
}
