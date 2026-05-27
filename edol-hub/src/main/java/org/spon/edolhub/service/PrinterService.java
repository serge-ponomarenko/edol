package org.spon.edolhub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edol.model.PrinterState;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrinterService {

    private final RestClient edolCoreClient;

    public PrinterState getState() {
        try {
            return edolCoreClient.get()
                    .uri("/printer/state")
                    .retrieve()
                    .body(PrinterState.class);
        } catch (Exception e) {
            log.warn(
                    "EDOL Core unavailable"
            );
            return null;
        }
    }

}
