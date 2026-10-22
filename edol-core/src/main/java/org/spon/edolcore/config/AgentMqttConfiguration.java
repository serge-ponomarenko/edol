package org.spon.edolcore.config;

import org.spon.edolcore.service.agent.mqtt.*;
import org.spon.edolcore.service.agent.pairing.AgentCredentialCodec;
import org.spon.edolcore.service.tenant.CoreTenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AgentMqttProperties.class)
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}")
public class AgentMqttConfiguration {
    @Bean
    AgentBroker agentBroker(@Value("${mqttServer.url}") String url, AgentMqttProperties properties) {
        return new MosquittoAgentBroker(url, properties);
    }

    @Bean
    AgentMqttService agentMqttService(JdbcTemplate jdbc, CoreTenantContext tenants, AgentBroker broker,
                                    AgentCredentialCodec codec, PlatformTransactionManager manager) {
        return new AgentMqttService(jdbc, tenants, broker, codec, manager);
    }
}
