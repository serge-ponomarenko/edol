package org.spon.edolams.service;

import org.spon.edol.model.PrinterState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
public class PrinterService {

    private final RestClient edolCoreClient;
    private final AmsPrinterTenantResolver tenantResolver;

    public PrinterService(
            @Qualifier("edolCoreRestClient") RestClient edolCoreClient,
            AmsPrinterTenantResolver tenantResolver
    ) {
        this.edolCoreClient = edolCoreClient;
        this.tenantResolver = tenantResolver;
    }

    public PrinterState getState(UUID printerId) {
        return tenantResolver.withPrinter(printerId, () -> edolCoreClient.get()
                .uri("/api/printers/{printerId}/state", printerId)
                .retrieve()
                .body(PrinterState.class));
    }

}
