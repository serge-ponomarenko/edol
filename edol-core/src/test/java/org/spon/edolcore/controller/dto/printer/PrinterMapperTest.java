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

    @Test
    void normalizesBlankOptionalAgentIdsToNull() {
        PrinterConnectionDto connectionDto = new PrinterConnectionDto(
                null, null, null, null, null, null, "  "
        );
        CreatePrinterRequest request = new CreatePrinterRequest(
                "P1", "Printer", null, null, PrinterModel.BAMBU_P1S,
                PrinterConnectionMode.DIRECT, PrinterCameraProvider.LEGACY,
                true, connectionDto
        );

        var firstConnection = mapper.createConnection(mapper.createPrinter(request), connectionDto);
        var secondConnection = mapper.createConnection(mapper.createPrinter(request), connectionDto);

        assertThat(firstConnection.getAgentId()).isNull();
        assertThat(secondConnection.getAgentId()).isNull();
    }

    @Test
    void normalizesBlankAgentIdWhenUpdatingAConnection() {
        var connection = new org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration();

        mapper.updateConnection(connection, new UpdatePrinterConnectionRequest(
                null, null, null, null, null, null, ""
        ));

        assertThat(connection.getAgentId()).isNull();
    }
}
