package org.spon.edolcore.service.agent.mqtt;

import java.util.List;
import java.util.UUID;

public final class AgentMqttTopics {
    public static final String PREFIX = "edol/agent/v1/";
    public static final List<String> REPORTS = List.of("printer/report", "heartbeat", "events");
    public static final List<String> COMMANDS = List.of("commands/printer", "commands/agent");

    private AgentMqttTopics() { }

    public static String topic(UUID id, String suffix) { return PREFIX + id + "/" + suffix; }
    public static String username(UUID id) { return "agent:" + id; }
    public static String clientId(UUID id) { return "edol-agent:" + id; }

    public static UUID ingressId(String topic) {
        if (topic == null || !topic.startsWith(PREFIX) || topic.length() < PREFIX.length() + 38) {
            throw new IllegalArgumentException("Invalid secure agent topic");
        }
        String value = topic.substring(PREFIX.length(), PREFIX.length() + 36);
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value) || topic.charAt(PREFIX.length() + 36) != '/'
                || !REPORTS.contains(topic.substring(PREFIX.length() + 37))) {
            throw new IllegalArgumentException("Invalid secure agent topic");
        }
        return id;
    }
}
