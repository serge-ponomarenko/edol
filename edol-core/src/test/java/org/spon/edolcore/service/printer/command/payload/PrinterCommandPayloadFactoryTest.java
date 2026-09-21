package org.spon.edolcore.service.printer.command.payload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterCommandPayloadFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void createsBambuPrintSpeedPayload(int level) throws Exception {
        var payload = objectMapper.readTree(PrinterCommandPayloadFactory.printSpeed(level));

        assertThat(payload.at("/print/command").asText()).isEqualTo("print_speed");
        assertThat(payload.at("/print/sequence_id").asText()).isEqualTo("0");
        assertThat(payload.at("/print/param").asText()).isEqualTo(String.valueOf(level));
    }
}
