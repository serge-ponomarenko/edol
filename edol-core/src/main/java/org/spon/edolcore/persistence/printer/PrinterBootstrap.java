package org.spon.edolcore.persistence.printer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrinterBootstrap {

    private final PrinterRepository printerRepository;
    private final PrinterConnectionConfigurationRepository configurationRepository;

    @Value("${bambu.host}")
    private String bambuHost;

    @Value("${bambu.serial}")
    private String serialNumber;

    @Value("${bambu.access-code}")
    private String accessCode;

    @Value("${edol.printer.connection-mode}")
    private String connectionMode;

    @Value("${edol.agent.id}")
    private String agentId;

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {

        if (printerRepository.count() > 0) {
            return;
        }

        Instant now = Instant.now();

        Printer printer = new Printer();
        printer.setId(UUID.randomUUID());
        printer.setDisplayId("PR-001");
        printer.setName("Default Printer");
        printer.setDescription("Auto-generated bootstrap printer");
        printer.setSerialNumber(serialNumber);
        printer.setModel(PrinterModel.UNKNOWN);
        printer.setConnectionMode(
                PrinterConnectionMode.valueOf(connectionMode.toUpperCase())
        );
        printer.setEnabled(true);
        printer.setCreatedAt(now);
        printer.setUpdatedAt(now);

        printerRepository.save(printer);

        PrinterConnectionConfiguration configuration =
                new PrinterConnectionConfiguration();

        configuration.setId(UUID.randomUUID());
        configuration.setPrinter(printer);

        if (printer.getConnectionMode() == PrinterConnectionMode.DIRECT) {
            configuration.setMqttHost(bambuHost);
            configuration.setFtpHost(bambuHost);
            configuration.setAccessCode(accessCode);
        }

        if (printer.getConnectionMode() == PrinterConnectionMode.AGENT) {
            configuration.setAgentId(agentId);
        }

        configurationRepository.save(configuration);

        log.info(
                "Bootstrap printer created: {} ({})",
                printer.getDisplayId(),
                printer.getSerialNumber()
        );
    }
}