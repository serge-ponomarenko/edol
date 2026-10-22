package org.spon.edolcore.service.agent.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("edol-core.agent.mqtt")
public record AgentMqttProperties(boolean enabled, String adminUsername, String adminPassword) {
    @Override public String toString() { return "AgentMqttProperties[REDACTED]"; }
}
