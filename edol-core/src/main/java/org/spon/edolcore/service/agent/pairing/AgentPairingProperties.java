package org.spon.edolcore.service.agent.pairing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties("edol-core.agent")
public record AgentPairingProperties(@DefaultValue("1") int keyVersion,
                                    Map<Integer, String> pairingKeys,
                                    Map<Integer, String> credentialKeys) {
    @Override
    public String toString() {
        return "AgentPairingProperties[keyVersion=" + keyVersion + ", keys=REDACTED]";
    }
}
