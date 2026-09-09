---
name: edol-runtime-contract
description: Safely change EDOL printer runtime, telemetry, MQTT events, or Core state contracts that may affect multiple services.
---

# EDOL Runtime Contract Workflow

Use this skill when changing Core printer runtime, direct or agent telemetry, MQTT topics/payloads, printer state, recovery, or consumers in Hub, Notify, or AMS.

1. Trace the complete path: telemetry source, printer resolution, runtime state, application event, MQTT or HTTP contract, and every consumer.
2. Treat event type, topic, payload fields, printer identity, session identity, ordering, and startup recovery as contract elements. Make coordinated changes to every affected producer and consumer.
3. Preserve the separation between Core-owned printer connectivity/runtime state and Hub-owned operational persistence.
4. Do not use live printers, publish MQTT commands, or deploy services merely to validate a code change. Report when local checks cannot prove device or broker behavior.
5. Update `docs/architecture.md` only if the established runtime flow or contract changes.
