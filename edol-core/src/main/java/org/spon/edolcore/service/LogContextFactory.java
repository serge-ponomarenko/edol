package org.spon.edolcore.service;

import org.slf4j.spi.LoggingEventBuilder;
import org.spon.edolcore.service.print.ActivePrintContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogContextFactory {

    private static final String PRINTER_ID_KEY = "printerId";

    public LoggingEventBuilder system(
            LoggingEventBuilder log) {

        return log;
    }

    public LoggingEventBuilder context(
            LoggingEventBuilder log,
            ActivePrintContext context) {

        return log
                .addKeyValue(PRINTER_ID_KEY, context.getPrinterId())
                .addKeyValue("sessionId", context.getSessionId())
                .addKeyValue("gcodeFile", context.getGcodeFile());
    }

    public LoggingEventBuilder session(
            LoggingEventBuilder log,
            UUID printerId,
            String sessionId
    ) {

        return log
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .addKeyValue("sessionId", sessionId);
    }

    public LoggingEventBuilder printer(
            LoggingEventBuilder log,
            UUID printerId) {

        return log
                .addKeyValue(PRINTER_ID_KEY, printerId);
    }

    public LoggingEventBuilder agent(
            LoggingEventBuilder log,
            UUID printerId,
            String agentId) {

        return log
                .addKeyValue(PRINTER_ID_KEY, printerId)
                .addKeyValue("agentId", agentId);
    }
}
