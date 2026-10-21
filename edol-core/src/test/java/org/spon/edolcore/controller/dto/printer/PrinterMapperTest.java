package org.spon.edolcore.controller.dto.printer;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.PrinterCameraProvider;
import org.spon.edolcore.persistence.printer.PrinterConnectionMode;
import org.spon.edolcore.persistence.printer.PrinterModel;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterMapperTest {

    private final PrinterMapper mapper = new PrinterMapper();

    @Test
    void keepsAccessCodeOnlyInTheWriteContract() throws Exception {
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = objectMapper.readValue("{\"accessCode\":\"synthetic-secret\"}", PrinterConnectionRequest.class);
        var connection = mapper.createConnection(new org.spon.edolcore.persistence.printer.Printer(), request);

        assertThat(connection.getAccessCode()).isEqualTo("synthetic-secret");
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(mapper.toDto(connection)))
                .has("accessCode")).isFalse();
    }

    @Test
    void preservesExistingAccessCodeForMissingOrBlankInputAndReplacesItOnlyWhenProvided() {
        var connection = new org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration();
        connection.setAccessCode("original-synthetic-secret");
        for (String input : new String[]{null, "", "  "}) {
            mapper.updateConnection(connection, new UpdatePrinterConnectionRequest(
                    null, null, null, null, null, input, null));
            assertThat(connection.getAccessCode()).isEqualTo("original-synthetic-secret");
        }
        mapper.updateConnection(connection, new UpdatePrinterConnectionRequest(
                null, null, null, null, null, "replacement-synthetic-secret", null));
        assertThat(connection.getAccessCode()).isEqualTo("replacement-synthetic-secret");
    }

    @Test
    void generatesVersionSevenIdsForNewPrinterAndConnection() {
        CreatePrinterRequest request = new CreatePrinterRequest(
                "P1", "Printer", null, null, PrinterModel.BAMBU_P1S,
                PrinterConnectionMode.DIRECT, PrinterCameraProvider.LEGACY,
                true, new PrinterConnectionRequest(null, null, null, null, null, null, null)
        );

        var printer = mapper.createPrinter(request);
        var connection = mapper.createConnection(printer, request.connection());

        assertThat(printer.getId().version()).isEqualTo(7);
        assertThat(connection.getId().version()).isEqualTo(7);
    }

    @Test
    void normalizesBlankOptionalAgentIdsToNull() {
        PrinterConnectionRequest connectionDto = new PrinterConnectionRequest(
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
