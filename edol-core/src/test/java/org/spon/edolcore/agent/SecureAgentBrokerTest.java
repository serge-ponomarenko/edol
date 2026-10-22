package org.spon.edolcore.agent;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.spon.edolcore.service.agent.mqtt.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class SecureAgentBrokerTest {
    private static final String NOTIFY_RESULT = "notify";
    private static final String AMS_TOPIC = "edolcore/ams";
    private static final String CORE_TOPICS = "edolcore/#";
    private static final String HEARTBEAT = "heartbeat";
    private static final String AGENT_COMMAND = "commands/agent";
    private static final String PRINTER_ONLINE = "edolcore/printer/online";
    private static final String OTHER_SUFFIX = "-other";
    private static final String HUB = "hub-events";
    @Test
    void oneBrokerEnforcesTlsServiceAndAgentAclAndActiveRevocationAcrossRestart() throws Exception {
        try (MqttBrokerFixture broker = new MqttBrokerFixture()) {
            broker.start();
            exercise(broker);
        }
    }

    private void exercise(MqttBrokerFixture broker) throws Exception {
        var controller = new MosquittoAgentBroker(broker.url(), new AgentMqttProperties(true,
                "core-agent-control", MqttBrokerFixture.servicePassword("CONTROL")), broker.sockets());
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String secret = "synthetic-agent-mqtt-password-distinct-from-https";
        controller.provision(id, secret);
        controller.provision(other, secret + OTHER_SUFFIX);
        try (MqttClient agent = broker.connect(AgentMqttTopics.username(id), secret, AgentMqttTopics.clientId(id));
             MqttClient agentB = broker.connect(AgentMqttTopics.username(other), secret + OTHER_SUFFIX, AgentMqttTopics.clientId(other));
             MqttClient ingress = broker.connect("core-agent-ingress", MqttBrokerFixture.servicePassword("INGRESS"), "edolcore-subscriber");
             MqttClient commands = broker.connect("core-agent-commands", MqttBrokerFixture.servicePassword("COMMAND"), "edolcore-publisher");
             MqttClient core = broker.connect("core-events", MqttBrokerFixture.servicePassword("CORE_EVENTS"), "edolcore-events-publisher");
             MqttClient hub = broker.connect(HUB, MqttBrokerFixture.servicePassword("HUB"), "edolhub-secure-subscriber");
             MqttClient notify = broker.connect("notify-events", MqttBrokerFixture.servicePassword("NOTIFY"), "edolnotify-subscriber");
             MqttClient ams = broker.connect("ams-events", MqttBrokerFixture.servicePassword("AMS"), "edolams-subscriber")) {
            var reports = new LinkedBlockingQueue<String>();
            ingress.subscribe("edol/agent/v1/+/heartbeat", (topic, message) -> reports.add(topic));
            publish(agent, AgentMqttTopics.topic(id, HEARTBEAT), "valid");
            assertThat(reports.poll(5, TimeUnit.SECONDS)).isEqualTo(AgentMqttTopics.topic(id, HEARTBEAT));
            publish(agent, AgentMqttTopics.topic(other, HEARTBEAT), "foreign");
            assertThat(reports.poll(500, TimeUnit.MILLISECONDS)).isNull();
            assertThat(agent.subscribeWithResponse("#").getGrantedQos()).containsExactly(128);
            assertThat(agent.subscribeWithResponse("edol/agent/v1/+/commands/agent").getGrantedQos()).containsExactly(128);
            assertThat(agent.subscribeWithResponse("$share/test/" + AgentMqttTopics.topic(id, AGENT_COMMAND))
                    .getGrantedQos()).containsExactly(128);
            assertThat(agent.subscribeWithResponse(CORE_TOPICS).getGrantedQos()).containsExactly(128);
            assertThat(agent.subscribeWithResponse("$CONTROL/dynamic-security/v1/response").getGrantedQos()).containsExactly(128);
            var received = new LinkedBlockingQueue<String>();
            agent.subscribe(AgentMqttTopics.topic(id, AGENT_COMMAND), (topic, message) -> received.add("command"));
            publish(commands, AgentMqttTopics.topic(id, AGENT_COMMAND), "safe-synthetic-command");
            assertThat(received.poll(5, TimeUnit.SECONDS)).isEqualTo("command");
            var services = new LinkedBlockingQueue<String>();
            hub.subscribe(CORE_TOPICS, (topic, message) -> services.add("hub"));
            notify.subscribe(PRINTER_ONLINE, (topic, message) -> services.add(NOTIFY_RESULT));
            ams.subscribe(AMS_TOPIC, (topic, message) -> services.add("ams"));
            publish(core, PRINTER_ONLINE, "synthetic-service-event");
            publish(core, AMS_TOPIC, "synthetic-service-event");
            assertThat(java.util.List.of(services.poll(5, TimeUnit.SECONDS), services.poll(5, TimeUnit.SECONDS),
                    services.poll(5, TimeUnit.SECONDS), services.poll(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("hub", NOTIFY_RESULT, "hub", "ams");
            publish(agent, PRINTER_ONLINE, "spoofed-core-event");
            assertThat(services.poll(500, TimeUnit.MILLISECONDS)).isNull();
            publish(hub, AgentMqttTopics.topic(id, AGENT_COMMAND), "forbidden-service-command");
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
            assertThat(hub.subscribeWithResponse("edol/agent/v1/#").getGrantedQos()).containsExactly(128);
            assertThat(ams.subscribeWithResponse(PRINTER_ONLINE).getGrantedQos()).containsExactly(128);
            controller.disable(id);
            org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !agent.isConnected());
            assertThat(agent.isConnected()).isFalse();
            publish(commands, AgentMqttTopics.topic(id, AGENT_COMMAND), "after-revoke");
            assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
            assertThat(agentB.isConnected()).isTrue();
            publish(core, PRINTER_ONLINE, "after-revoke");
            assertThat(services.poll(5, TimeUnit.SECONDS)).isNotNull();
            rejects(broker, AgentMqttTopics.username(id), secret, AgentMqttTopics.clientId(id));
            rejects(broker, AgentMqttTopics.username(other), "invalid", AgentMqttTopics.clientId(other));
            rejects(broker, AgentMqttTopics.username(other), secret + OTHER_SUFFIX, "spoofed-client-id");
        }
        broker.restart();
        // Restart preserves the broker's disabled identity and another Agent's valid authority.
        try (MqttClient recovered = broker.connect(AgentMqttTopics.username(other), secret + OTHER_SUFFIX, AgentMqttTopics.clientId(other))) {
            assertThat(recovered.isConnected()).isTrue();
            rejects(broker, AgentMqttTopics.username(id), secret, AgentMqttTopics.clientId(id));
        }
        try (MqttClient core = broker.connect("core-events", MqttBrokerFixture.servicePassword("CORE_EVENTS"), "edolcore-events-publisher");
             MqttClient hub = broker.connect(HUB, MqttBrokerFixture.servicePassword("HUB"), "edolhub-secure-subscriber");
             MqttClient notify = broker.connect("notify-events", MqttBrokerFixture.servicePassword("NOTIFY"), "edolnotify-subscriber");
             MqttClient ams = broker.connect("ams-events", MqttBrokerFixture.servicePassword("AMS"), "edolams-subscriber")) {
            var recovered = new LinkedBlockingQueue<String>();
            hub.subscribe(CORE_TOPICS, (topic, message) -> recovered.add("hub"));
            notify.subscribe(PRINTER_ONLINE, (topic, message) -> recovered.add(NOTIFY_RESULT));
            ams.subscribe(AMS_TOPIC, (topic, message) -> recovered.add("ams"));
            publish(core, PRINTER_ONLINE, "recovered");
            publish(core, AMS_TOPIC, "recovered");
            assertThat(java.util.List.of(recovered.poll(5, TimeUnit.SECONDS), recovered.poll(5, TimeUnit.SECONDS),
                    recovered.poll(5, TimeUnit.SECONDS), recovered.poll(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("hub", NOTIFY_RESULT, "hub", "ams");
        }
    }

    private static void rejects(MqttBrokerFixture broker, String user, String password, String id) {
        assertThatThrownBy(() -> {
            try (var client = new MqttClient(broker.url(), id, new MemoryPersistence())) {
                client.connect(broker.options(user, password));
            }
        }).isInstanceOf(org.eclipse.paho.client.mqttv3.MqttException.class);
    }

    private static void publish(MqttClient client, String topic, String payload) throws Exception {
        client.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
    }

    @Test
    void rejectsWrongHostnameExpiredAndUnknownTrustAndPlaintextWithoutFallback() throws Exception {
        for (boolean expired : new boolean[]{false, true}) {
            try (MqttBrokerFixture broker = new MqttBrokerFixture()) {
                broker.start(!expired, expired);
                rejects(broker, HUB, MqttBrokerFixture.servicePassword("HUB"), "denied-tls");
            }
        }
        try (MqttBrokerFixture broker = new MqttBrokerFixture()) {
            broker.start();
            javax.net.ssl.SSLContext untrusted = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            java.security.KeyStore empty = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            empty.load(null, null);
            var trust = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            trust.init(empty);
            untrusted.init(null, trust.getTrustManagers(), null);
            assertThatThrownBy(() -> {
                try (var client = new MqttClient(broker.url(), "unknown-ca", new MemoryPersistence())) {
                    var options = broker.options(HUB, MqttBrokerFixture.servicePassword("HUB"));
                    options.setSocketFactory(untrusted.getSocketFactory());
                    client.connect(options);
                }
            }).isInstanceOf(org.eclipse.paho.client.mqttv3.MqttException.class);
            assertThatThrownBy(() -> {
                try (var client = new MqttClient(broker.url().replace("ssl://", "tcp://"), "plaintext", new MemoryPersistence())) {
                    var options = broker.options(HUB, MqttBrokerFixture.servicePassword("HUB"));
                    options.setSocketFactory(null);
                    client.connect(options);
                }
            }).isInstanceOf(org.eclipse.paho.client.mqttv3.MqttException.class);
        }
    }
}
