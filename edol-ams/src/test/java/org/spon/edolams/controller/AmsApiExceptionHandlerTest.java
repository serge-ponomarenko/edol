package org.spon.edolams.controller;

import org.junit.jupiter.api.Test;
import org.spon.edolams.service.UnmappedAmsPrinterException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmsApiExceptionHandlerTest {

    @Test
    void returnsNotFoundForAnUnmappedPrinter() {
        var response = new AmsApiExceptionHandler()
                .handleUnmappedPrinter(new UnmappedAmsPrinterException(UUID.randomUUID()));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
