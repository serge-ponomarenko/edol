package org.spon.edolams.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

/** Explicit target binding for the disposable Stage 8 terminal simulator. */
@ConfigurationProperties("edol-ams.terminal-simulator")
public record Stage8TerminalSimulatorProperties(
        boolean enabled,
        UUID tenantId,
        UUID printerId
) {
}
