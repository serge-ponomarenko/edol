package org.spon.edolcore.service.printermqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.spon.edolcore.event.PrinterEventType;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.util.SslUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BambuMqttClient implements MqttCallback {

    private final PrinterStateService stateService;
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttClient client;

    @Value("${bambu.host}")
    private String host;

    @Value("${bambu.serial}")
    private String serial;

    @Value("${bambu.access-code}")
    private String accessCode;

    @Value("${bambu.client-id}")
    private String clientId;

    @Value("${bambu.connection-timeout}")
    private Integer bambuConnectionTimeout;

    @Value("${bambu.show-raw-mqtt}")
    private boolean showRawMqttMessages;

    public synchronized void connect() {
        try {

            if (client != null && client.isConnected()) {
                return;
            }

            client = new MqttClient(host, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("bblp");
            options.setPassword(accessCode.toCharArray());
            options.setAutomaticReconnect(false);
            options.setSocketFactory(SslUtil.createTrustAllSocketFactory());
            options.setKeepAliveInterval(60);

            // Disable hostname verification
            options.setHttpsHostnameVerificationEnabled(false);
            options.setConnectionTimeout(bambuConnectionTimeout);  // seconds

            client.setCallback(this);

            client.connect(options);

            client.subscribe("device/" + serial + "/report");

            log.info("Connected to Bambu MQTT");

            stateService.getState().setOnline(true);
            stateService.publish(PrinterEventType.PRINTER_ONLINE);

        } catch (Exception e) {
            log.warn("Printer connection failed: {}", e.getMessage());
        }

    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.error("MQTT connection lost");
        stateService.getState().setOnline(false);
        stateService.publish(PrinterEventType.PRINTER_OFFLINE);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            JsonNode root = mapper.readTree(message.getPayload());

            if (showRawMqttMessages) {
                log.info(String.valueOf(root));
            }

            if (root.has("print")) {
                JsonNode print = root.get("print");
                stateService.update(print);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    public void pause() {
        String payload = """
                {
                  "print": {
                    "command": "pause"
                  }
                }
                """;

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            client.publish("device/" + serial + "/request", message);
        } catch (MqttException e) {
            log.error("Cannot send PAUSE command!");
        }
    }

    public void stop() {
        String payload = """
                {
                  "print": {
                    "command": "stop"
                  }
                }
                """;

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            client.publish("device/" + serial + "/request", message);
        } catch (MqttException e) {
            log.error("Cannot send STOP command!");
        }
    }

    public void resume() {
        String payload = """
                {
                  "print": {
                    "command": "resume"
                  }
                }
                """;

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            client.publish("device/" + serial + "/request", message);
        } catch (MqttException e) {
            log.error("Cannot send RESUME command!");
        }
    }

    public void pushAll() {
        String payload = """
                {
                    "pushing": {
                        "sequence_id": "0",
                        "command": "pushall",
                        "version": 1,
                        "push_target": 1
                    }
                }
                """;

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            client.publish("device/" + serial + "/request", message);
        } catch (MqttException e) {
            log.error("Cannot send PUSHALL command!");
        }
    }

    public void skipObjects(List<Integer> objectsIds) {
        String joinedIds = objectsIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        long timestamp = System.currentTimeMillis() / 1000;
        String payload = """
                {
                    "print": {
                        "command": "skip_objects",
                        "timestamp": %d,
                        "obj_list": [
                            %s
                        ]
                    }
                }
                """.formatted(timestamp, joinedIds);

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            client.publish("device/" + serial + "/request", message);

            stateService.getState().getPrintObjects().forEach(po -> {
                if (objectsIds.contains(po.getId())) {
                    po.setSkipped(true);
                }
            });
        } catch (MqttException e) {
            log.error("Cannot send SKIP_OBJECT command!");
        }
    }

    public void spoolChange(SpoolChangeRequest spoolChangeRequest) {
        String payload = """
                {
                    "print": {
                        "command": "ams_filament_setting",
                        "ams_id": %d,
                        "tray_id": %d,
                        "tray_info_idx": "%s",
                        "tray_color": "%s",
                        "nozzle_temp_min": 0,
                        "nozzle_temp_max": 0,
                        "tray_type": "%s"
                    }
                }
                """.formatted(
                spoolChangeRequest.getAmsId(),
                spoolChangeRequest.getTrayId(),
                spoolChangeRequest.getTrayInfoIdx(),
                spoolChangeRequest.getTrayColor(),
                spoolChangeRequest.getTrayType()
        );

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            client.publish("device/" + serial + "/request", message);
        } catch (MqttException e) {
            log.error("Cannot send AMS_FILAMENT_SETTING command!");
        }
    }

    @Data
    public static class SpoolChangeRequest {
        Integer amsId;          // Index of the ams
        Integer trayId;         // Index of the tray
        String trayInfoIdx;     // Probably the setting ID of the filament profile
        String trayColor;       // Formatted as hex RRGGBBAA (alpha is always FF)
        String trayType;        // Type of filament, such as "PLA" or "ABS"
    }

}