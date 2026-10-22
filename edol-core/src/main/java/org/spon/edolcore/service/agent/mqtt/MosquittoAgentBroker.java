package org.spon.edolcore.service.agent.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.spon.edolcoreapi.security.SecureMqttEndpoint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** The control account is server-only and has authority over the single shared broker. */
public final class MosquittoAgentBroker implements AgentBroker {
    public static final String CONTROL = "$CONTROL/dynamic-security/v1";
    private static final String COMMAND = "command";
    private static final String USERNAME = "username";
    private static final String ERROR = "error";
    private final String url;
    private final AgentMqttProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final javax.net.ssl.SSLSocketFactory sockets;

    public MosquittoAgentBroker(String url, AgentMqttProperties properties) {
        this(url, properties, null);
    }

    public MosquittoAgentBroker(String url, AgentMqttProperties properties, javax.net.ssl.SSLSocketFactory sockets) {
        SecureMqttEndpoint.validate(true, url, properties.adminUsername(), properties.adminPassword());
        this.url = url;
        this.properties = properties;
        this.sockets = sockets;
    }

    @Override
    public void provision(UUID id, String password) {
        List<Map<String, Object>> acls = new ArrayList<>();
        for (String suffix : AgentMqttTopics.REPORTS) {
            acls.add(acl("publishClientSend", AgentMqttTopics.topic(id, suffix)));
        }
        for (String suffix : AgentMqttTopics.COMMANDS) {
            acls.add(acl("subscribeLiteral", AgentMqttTopics.topic(id, suffix)));
            acls.add(acl("publishClientReceive", AgentMqttTopics.topic(id, suffix)));
        }
        String role = "agent:" + id;
        command(Map.of(COMMAND, "createRole", "rolename", role, "acls", acls));
        // Create without a password first: partial provisioning cannot authenticate.
        command(Map.of(COMMAND, "createClient", USERNAME, AgentMqttTopics.username(id),
                "clientid", AgentMqttTopics.clientId(id), "roles", List.of(Map.of("rolename", role))));
        command(Map.of(COMMAND, "setClientPassword", USERNAME, AgentMqttTopics.username(id), "password", password));
    }

    @Override
    public void disable(UUID id) {
        // Missing client is already safely disabled (e.g. failed provisioning before createClient).
        JsonNode result = commandResult(Map.of(COMMAND, "disableClient", USERNAME, AgentMqttTopics.username(id)));
        if (result.has(ERROR) && !"Client not found".equals(result.path(ERROR).asText())) {
            throw unavailable();
        }
    }

    private static Map<String, Object> acl(String type, String topic) {
        return Map.of("acltype", type, "topic", topic, "allow", true);
    }

    private void command(Map<String, Object> command) {
        if (commandResult(command).has(ERROR)) throw unavailable();
    }

    private JsonNode commandResult(Map<String, Object> command) {
        String correlation = UUID.randomUUID().toString();
        var input = new java.util.HashMap<>(command);
        input.put("correlationData", correlation);
        MqttClient client = null;
        try {
            client = new MqttClient(url, "core-agent-control-" + UUID.randomUUID(), new MemoryPersistence());
            client.setTimeToWait(10000);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(properties.adminUsername());
            options.setPassword(properties.adminPassword().toCharArray());
            options.setHttpsHostnameVerificationEnabled(true);
            options.setConnectionTimeout(10);
            options.setCleanSession(true);
            if (sockets != null) options.setSocketFactory(sockets);
            client.connect(options);
            CompletableFuture<JsonNode> response = new CompletableFuture<>();
            client.subscribe(CONTROL + "/response", 1, (topic, message) ->
                    completeResponse(response, message.getPayload(), correlation, command.get(COMMAND)));
            client.publish(CONTROL, mapper.writeValueAsString(Map.of("commands", List.of(input)))
                    .getBytes(StandardCharsets.UTF_8), 1, false);
            return response.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) {
            // Never attach broker payloads, passwords or raw responses to an exception.
            throw unavailable();
        } finally {
            close(client);
        }
    }

    private void completeResponse(CompletableFuture<JsonNode> response, byte[] payload, String correlation, Object name) {
        if (payload.length > 65536) return;
        try {
            for (JsonNode result : mapper.readTree(payload).path("responses")) {
                if (correlation.equals(result.path("correlationData").asText())
                        && name.equals(result.path(COMMAND).asText())) response.complete(result);
            }
        } catch (java.io.IOException exception) {
            response.completeExceptionally(unavailable());
        }
    }

    private static void close(MqttClient client) {
        if (client == null) return;
        try {
            if (client.isConnected()) client.disconnect();
            client.close();
        } catch (org.eclipse.paho.client.mqttv3.MqttException ignored) {
            // Request already has its outcome.
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Agent broker operation could not be confirmed");
    }
}
