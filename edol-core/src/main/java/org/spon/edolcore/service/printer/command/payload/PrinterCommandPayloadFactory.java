package org.spon.edolcore.service.printer.command.payload;

import org.spon.edolcore.model.dto.SpoolChangeRequestDto;

import java.util.List;
import java.util.stream.Collectors;

public final class PrinterCommandPayloadFactory {

    private static final String PAUSE_PAYLOAD = """
            {
              "print": {
                "command": "pause"
              }
            }
            """;
    private static final String RESUME_PAYLOAD = """
            {
              "print": {
                "command": "resume"
              }
            }
            """;
    private static final String STOP_PAYLOAD = """
            {
              "print": {
                "command": "stop"
              }
            }
            """;
    private static final String PUSH_ALL_PAYLOAD = """
            {
                "pushing": {
                    "sequence_id": "0",
                    "command": "pushall",
                    "version": 1,
                    "push_target": 1
                }
            }
            """;
    private static final String SKIP_OBJECTS_PAYLOAD = """
            {
                "print": {
                    "command": "skip_objects",
                    "timestamp": %d,
                    "obj_list": [
                        %s
                    ]
                }
            }
            """;
    private static final String SPOOL_CHANGE_PAYLOAD = """
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
            """;

    private PrinterCommandPayloadFactory() {
    }

    public static String pause() {

        return PAUSE_PAYLOAD;
    }

    public static String resume() {
        return RESUME_PAYLOAD;
    }

    public static String stop() {
        return STOP_PAYLOAD;
    }

    public static String pushAll() {
        return PUSH_ALL_PAYLOAD;
    }

    public static String skipObjects(List<Integer> objectIds) {
        String joinedIds = objectIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));

        long timestamp = System.currentTimeMillis() / 1000;

        return SKIP_OBJECTS_PAYLOAD.formatted(timestamp, joinedIds);
    }

    public static String spoolChange(SpoolChangeRequestDto request) {
        return SPOOL_CHANGE_PAYLOAD.formatted(
                request.getAmsId(),
                request.getTrayId(),
                request.getTrayInfoIdx(),
                request.getTrayColor(),
                request.getTrayType()
        );
    }
}