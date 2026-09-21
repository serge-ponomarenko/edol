package org.spon.edolcore.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolcore.service.LogContextFactory;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.model.metadata.ModelMetadataWorkflowService;
import org.spon.edolcore.service.printer.command.PrinterCommandGateway;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PrinterCommandControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Spy
    private LogContextFactory logContextFactory = new LogContextFactory();

    @Mock
    private PrinterStateService printerStateService;

    @Mock
    private PrinterCommandGateway printerCommandGateway;

    @Mock
    private DefaultPrinterResolver defaultPrinterResolver;

    @Mock
    private ModelMetadataWorkflowService modelMetadataWorkflowService;

    @InjectMocks
    private PrinterCommandController controller;

    @Test
    void sendsValidatedPrintSpeedLevelToPrinterGateway() {
        PrinterCommandController.PrintSpeedRequest request =
                new PrinterCommandController.PrintSpeedRequest();
        request.setLevel(3);

        var response = controller.setPrintSpeed(PRINTER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(printerCommandGateway).setPrintSpeed(PRINTER_ID, 3);
    }

    @Test
    void rejectsUnsupportedPrintSpeedLevel() {
        PrinterCommandController.PrintSpeedRequest request =
                new PrinterCommandController.PrintSpeedRequest();
        request.setLevel(5);

        var response = controller.setPrintSpeed(PRINTER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(printerCommandGateway);
    }
}
