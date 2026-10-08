package org.spon.edolhub.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

@Configuration
@EnableIntegration
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
@ConditionalOnProperty(name = "edol-hub.runtime.mqtt-enabled", havingValue = "true")
public class SecureMqttConfig {

    @Value("${mqttServer.url}")
    private String mqttServerUrl;

    @Value("${edol-hub.mqtt.client-id:edolhub-secure-subscriber}")
    private String clientId;

    @Value("${edol-hub.mqtt.username:}")
    private String username;

    @Value("${edol-hub.mqtt.password:}")
    private String password;

    @Bean
    public MqttPahoClientFactory secureMqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUrl});
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel secureMqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer secureInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId,
                secureMqttClientFactory(),
                "edolcore/#"
        );
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setManualAcks(true);
        adapter.setQos(1);
        adapter.setOutputChannel(secureMqttInputChannel());
        return adapter;
    }
}
