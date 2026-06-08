package org.spon.edolcore.service.agent.command.payload;

public final class AgentCommandPayloadFactory {

    private static final String UPLOAD_MODEL_PAYLOAD = """
            {
                "command": "UPLOAD_MODEL",
                "payload": {
                    "fileName": "%s"
                }
            }
            """;

    private static final String ENABLE_SNAPSHOT_SCHEDULER_PAYLOAD = """
            {
                "command": "ENABLE_SNAPSHOT_SCHEDULER"
            }
            """;

    private static final String DISABLE_SNAPSHOT_SCHEDULER_PAYLOAD = """
            {
                "command": "DISABLE_SNAPSHOT_SCHEDULER"
            }
            """;

    private AgentCommandPayloadFactory() {
    }

    public static String uploadModel(String fileName) {
        return UPLOAD_MODEL_PAYLOAD.formatted(fileName);
    }

    public static String enableSnapshotScheduler() {
        return ENABLE_SNAPSHOT_SCHEDULER_PAYLOAD;
    }

    public static String disableSnapshotScheduler() {
        return DISABLE_SNAPSHOT_SCHEDULER_PAYLOAD;
    }
}
