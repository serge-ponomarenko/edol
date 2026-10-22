package org.spon.edolcore.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

@EnableIntegration
@Configuration
public class MqttSubscribeConfig {

    private static final String TOPIC_PREFIX = "edol/agents/";

    @Value("${mqttServer.url}")
    private String mqttServerUrl;
    @Value("${edol.deployment.mode:home}")
    private String mode;
    @Value("${edol-core.agent.mqtt.ingress-username:}")
    private String username;
    @Value("${edol-core.agent.mqtt.ingress-password:}")
    private String password;

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${edol-core.runtime.mqtt-enabled:true} && ('${edol.deployment.mode:home}' != 'secure-multi-tenant' || (${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}))")
    public MqttPahoClientFactory mqttInboundClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUrl});
        boolean secure = "secure-multi-tenant".equals(mode);
        org.spon.edolcoreapi.security.SecureMqttEndpoint.validate(secure, mqttServerUrl, username, password);
        if (secure) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setHttpsHostnameVerificationEnabled(true);
        }
        options.setConnectionTimeout(10);

        factory.setConnectionOptions(options);

        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${edol-core.runtime.mqtt-enabled:true} && ('${edol.deployment.mode:home}' != 'secure-multi-tenant' || (${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}))")
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {

        String prefix = "secure-multi-tenant".equals(mode)
                ? org.spon.edolcore.service.agent.mqtt.AgentMqttTopics.PREFIX : TOPIC_PREFIX;
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        "edolcore-subscriber",
                        mqttInboundClientFactory(),
                        prefix + "+/printer/report",
                        prefix + "+/heartbeat",
                        prefix + "+/events"
                );

        adapter.setOutputChannel(mqttInboundChannel());

        return adapter;
    }
}
