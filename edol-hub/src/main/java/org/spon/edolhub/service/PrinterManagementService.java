package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.model.dto.CorePrinterConnectionDto;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.spon.edolhub.model.dto.PrinterForm;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrinterManagementService {

    private static final String PRINTER_ENDPOINT = "/api/printers/{printerId}";

    private final RestClient edolCoreClient;

    public CorePrinterDto getPrinter(UUID printerId) {
        return edolCoreClient.get()
                .uri(PRINTER_ENDPOINT, printerId)
                .retrieve()
                .body(CorePrinterDto.class);
    }

    public CorePrinterConnectionDto getConnection(UUID printerId) {
        return edolCoreClient.get()
                .uri("/api/printers/{printerId}/connection", printerId)
                .retrieve()
                .body(CorePrinterConnectionDto.class);
    }

    public CorePrinterDto createPrinter(PrinterForm form) {
        return edolCoreClient.post()
                .uri("/api/printers")
                .body(new PrinterRequest(form))
                .retrieve()
                .body(CorePrinterDto.class);
    }

    public void updatePrinter(UUID printerId, PrinterForm form) {
        edolCoreClient.patch()
                .uri(PRINTER_ENDPOINT, printerId)
                .body(new PrinterRequest(form))
                .retrieve()
                .toBodilessEntity();

        edolCoreClient.patch()
                .uri("/api/printers/{printerId}/connection", printerId)
                .body(new ConnectionRequest(form, getConnection(printerId).accessCode()))
                .retrieve()
                .toBodilessEntity();
    }

    public void deletePrinter(UUID printerId) {
        edolCoreClient.delete()
                .uri(PRINTER_ENDPOINT, printerId)
                .retrieve()
                .toBodilessEntity();
    }

    private record PrinterRequest(
            String displayId,
            String name,
            String description,
            String serialNumber,
            String model,
            String connectionMode,
            String cameraProvider,
            boolean enabled,
            ConnectionRequest connection
    ) {
        private PrinterRequest(PrinterForm form) {
            this(
                    form.getDisplayId(),
                    form.getName(),
                    form.getDescription(),
                    form.getSerialNumber(),
                    form.getModel(),
                    form.getConnectionMode(),
                    form.getCameraProvider(),
                    form.isEnabled(),
                    new ConnectionRequest(form)
            );
        }
    }

    private record ConnectionRequest(
            String mqttHost,
            Integer mqttPort,
            String ftpHost,
            Integer ftpPort,
            String modelDirectory,
            String accessCode,
            String agentId
    ) {
        private ConnectionRequest(PrinterForm form) {
            this(form, null);
        }

        private ConnectionRequest(PrinterForm form, String existingAccessCode) {
            this(
                    form.getMqttHost(),
                    form.getMqttPort(),
                    form.getFtpHost(),
                    form.getFtpPort(),
                    form.getModelDirectory(),
                    form.getAccessCode() == null || form.getAccessCode().isBlank()
                            ? existingAccessCode
                            : form.getAccessCode(),
                    form.getAgentId()
            );
        }
    }
}
