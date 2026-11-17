package org.spon.edolcore.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Non-secret deployment metadata, served only by the verified device HTTPS chain. */
@RestController
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false} && ${edol-core.agent.discovery.enabled:false}")
public class AgentDiscoveryController {
    private final Configuration configuration;

    public AgentDiscoveryController(@Value("${edol-core.agent.discovery.mqtt-host}") String host,
                                    @Value("${edol-core.agent.discovery.mqtt-port}") int port) {
        if (host.length() > 253 || !host.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid public Agent MQTT endpoint");
        }
        configuration = new Configuration(1, "3.1.1", host, port, true);
    }

    @GetMapping("/api/agent/v1/configuration")
    public Configuration configuration() {
        return configuration;
    }

    public record Configuration(int version, String mqttProtocol, String mqttHost, int mqttPort, boolean mqttTls) { }
}
