package org.spon.edolcore.controller.dto.printer;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.PrinterCameraProvider;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.persistence.printer.PrinterModel;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterMapperTest {

    private final PrinterMapper mapper = new PrinterMapper();

    @Test
    void generatesVersionSevenIdsForNewPrinterAndConnection() {
        CreatePrinterRequest request = new CreatePrinterRequest(
                "P1", "Printer", null, null, PrinterModel.BAMBU_P1S,
                PrinterConnectionMode.DIRECT, PrinterCameraProvider.LEGACY,
                true, new PrinterConnectionDto(null, null, null, null, null, null, null)
        );

        var printer = mapper.createPrinter(request);
        var connection = mapper.createConnection(printer, request.connection());

        assertThat(printer.getId().version()).isEqualTo(7);
        assertThat(connection.getId().version()).isEqualTo(7);
    }
}
