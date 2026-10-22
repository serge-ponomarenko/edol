package org.spon.edolcore.controller;

import org.spon.edolcore.service.agent.mqtt.AgentMqttService;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}")
public class AgentMqttController {
    private final AgentMqttService mqtt;

    public AgentMqttController(AgentMqttService mqtt) { this.mqtt = mqtt; }

    @PostMapping("/api/agent/v1/mqtt/credentials")
    public AgentMqttService.Credentials provision(@AuthenticationPrincipal AgentPairingService.Identity identity) {
        return mqtt.provision(identity);
    }
}
