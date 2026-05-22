package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.spon.edol.model.PrinterState;

@Service
@RequiredArgsConstructor
public class PrinterService {

    private final RestClient edolCoreClient;

    public PrinterState getState() {
        return edolCoreClient.get()
                .uri("/printer/state")
                .retrieve()
                .body(PrinterState.class);
    }

}
