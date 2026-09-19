package org.spon.edolhub.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.model.entity.Printer;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorePrinterProxyControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PrinterAccessService printerAccessService;

    @InjectMocks
    private CorePrinterProxyController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "edolCoreUrl", "http://edolcore:8080");
    }

    @Test
    void proxiesCameraSnapshotForRequestedPrinter() {
        Printer printer = new Printer();
        printer.setId(PRINTER_ID);
        byte[] image = "camera".getBytes();
        ResponseEntity<byte[]> coreResponse = ResponseEntity.status(HttpStatus.OK).body(image);
        when(printerAccessService.getPrinter(PRINTER_ID)).thenReturn(printer);
        when(restTemplate.exchange(
                eq("http://edolcore:8080/api/printers/" + PRINTER_ID + "/camera/snapshot"),
                eq(HttpMethod.GET),
                isNull(),
                eq(byte[].class)
        )).thenReturn(coreResponse);

        ResponseEntity<byte[]> response = controller.camera(PRINTER_ID);

        assertThat(response).isSameAs(coreResponse);
        verify(printerAccessService).getPrinter(PRINTER_ID);
    }
}
