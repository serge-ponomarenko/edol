package org.spon.edolcore.service.printermqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttReconnectScheduler {

    private final BambuMqttClient mqttClient;

    @Scheduled(fixedDelay = 30000)
    public void reconnect() {
        //log.info("---> Printer MQTTClient connection status: {}", mqttClient.isConnected());
        if (!mqttClient.isConnected()) {
            log.info("Attempting to connect to printer...");
            mqttClient.connect();
        }
    }

}
