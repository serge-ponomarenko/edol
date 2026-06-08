package org.spon.edolcore.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${edol.agent.id}")
    private String agentId;

    @Bean
    public MqttPahoClientFactory mqttInboundClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUrl});

        factory.setConnectionOptions(options);

        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        "edolcore-subscriber",
                        mqttInboundClientFactory(),
                        TOPIC_PREFIX + agentId + "/printer/report",
                        TOPIC_PREFIX + agentId + "/heartbeat",
                        TOPIC_PREFIX + agentId + "/events"
                );

        adapter.setOutputChannel(mqttInboundChannel());

        return adapter;
    }
}