package org.spon.edolhub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalModelAttributesTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private PrinterAccessService printerAccessService;

    @Test
    void persistsAValidPrinterFromThePathAndRestoresItOnTenantPages() {
        Printer printer = new Printer();
        printer.setId(PRINTER_ID);
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest printerRequest = request("/printers/" + PRINTER_ID, session);
        MockHttpServletRequest spoolRequest = request("/filament-spools", session);
        GlobalModelAttributes attributes = new GlobalModelAttributes(printerAccessService);

        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);

        assertThat(attributes.printerId(printerRequest)).isEqualTo(PRINTER_ID);
        assertThat(attributes.selectedPrinter(spoolRequest)).isSameAs(printer);
        assertThat(attributes.printerId(spoolRequest)).isEqualTo(PRINTER_ID);
        verify(printerAccessService, times(2)).getPrinter(PRINTER_ID);
    }

    @Test
    void clearsAnUnavailablePrinterStoredInTheSession() {
        Printer printer = new Printer();
        printer.setId(PRINTER_ID);
        MockHttpSession session = new MockHttpSession();
        GlobalModelAttributes attributes = new GlobalModelAttributes(printerAccessService);

        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        attributes.printerId(request("/printers/" + PRINTER_ID, session));
        when(printerAccessService.getPrinter(PRINTER_ID)).thenThrow(new IllegalArgumentException("Printer unavailable"));

        MockHttpServletRequest spoolRequest = request("/filament-spools", session);

        assertThat(attributes.printerId(spoolRequest)).isNull();
        assertThat(attributes.selectedPrinter(spoolRequest)).isNull();
    }

    private MockHttpServletRequest request(String uri, MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setSession(session);
        return request;
    }
}
