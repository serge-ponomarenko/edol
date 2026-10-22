package org.spon.edolcore.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@EnableIntegration
@Configuration
public class MqttPublishConfig {

    @Value("${mqttServer.url}")
    private String mqttServerUrl;
    @Value("${edol.deployment.mode:home}")
    private String mode;
    @Value("${edol-core.agent.mqtt.command-username:}")
    private String commandUsername;
    @Value("${edol-core.agent.mqtt.command-password:}")
    private String commandPassword;

    @Value("${edol-core.mqtt.integration.username:}")
    private String integrationUsername;

    @Value("${edol-core.mqtt.integration.password:}")
    private String integrationPassword;

    @Value("${edol-core.mqtt.integration.client-id:edolcore-events-publisher}")
    private String integrationClientId;

    @Bean
    @ConditionalOnExpression("${edol-core.runtime.mqtt-enabled:true} && ('${edol.deployment.mode:home}' != 'secure-multi-tenant' || (${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}))")
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setServerURIs(new String[]{mqttServerUrl});
        boolean secure = "secure-multi-tenant".equals(mode);
        org.spon.edolcoreapi.security.SecureMqttEndpoint.validate(secure, mqttServerUrl, commandUsername, commandPassword);
        if (secure) {
            mqttConnectOptions.setUserName(commandUsername);
            mqttConnectOptions.setPassword(commandPassword.toCharArray());
            mqttConnectOptions.setHttpsHostnameVerificationEnabled(true);
        }
        mqttConnectOptions.setConnectionTimeout(10);
        factory.setConnectionOptions(mqttConnectOptions);
        return factory;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel coreEventsOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    @ConditionalOnExpression("${edol-core.runtime.mqtt-enabled:true} && ('${edol.deployment.mode:home}' != 'secure-multi-tenant' || (${edol-core.agent.enabled:false} && ${edol-core.agent.mqtt.enabled:false}))")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler("edolcore-publisher", mqttClientFactory());

        handler.setAsync(true);
        handler.setDefaultTopic("edolcore/events");

        return handler;
    }

    @Bean
    @ServiceActivator(inputChannel = "coreEventsOutboundChannel")
    @ConditionalOnExpression("${edol-core.runtime.mqtt-enabled:true} || ${edol-core.runtime.integration-mqtt-enabled:false}")
    public MessageHandler coreEventsOutbound() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(integrationClientId, coreEventsClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic("edolcore/events");
        handler.setDefaultQos(1);
        return handler;
    }

    private MqttPahoClientFactory coreEventsClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttServerUrl});
        org.spon.edolcoreapi.security.SecureMqttEndpoint.validate("secure-multi-tenant".equals(mode),
                mqttServerUrl, integrationUsername, integrationPassword);
        options.setHttpsHostnameVerificationEnabled(true);
        options.setConnectionTimeout(10);
        if (!integrationUsername.isBlank()) {
            options.setUserName(integrationUsername);
            options.setPassword(integrationPassword.toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

}
