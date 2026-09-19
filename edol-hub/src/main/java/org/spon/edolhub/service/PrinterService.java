package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.spon.edolhub.model.dto.CorePrinterDto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterService {

    private final RestClient edolCoreClient;

    public List<CorePrinterDto> getPrinters() {
        CorePrinterDto[] printers = edolCoreClient.get()
                .uri("/api/printers")
                .retrieve()
                .body(CorePrinterDto[].class);

        return printers == null ? List.of() : Arrays.asList(printers);
    }

    public CorePrinterDto getPrinter(UUID printerId) {
        return edolCoreClient.get()
                .uri("/api/printers/{printerId}", printerId)
                .retrieve()
                .body(CorePrinterDto.class);
    }

    public PrinterState getState(UUID printerId) {
        try {
            return edolCoreClient.get()
                    .uri("/api/printers/{printerId}/state", printerId)
                    .retrieve()
                    .body(PrinterState.class);
        } catch (Exception e) {
            log.warn("EDOL Core unavailable for printer {}", printerId);
            return null;
        }
    }

}
