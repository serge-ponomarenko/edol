package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.model.dto.PrinterForm;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrinterManagementServiceTest {

    @Test
    void editsPrinterWithoutReadingItsExistingSecret() {
        assertConnectionUpdate("", "null");
    }

    @Test
    void sendsAnExplicitReplacementSecretOnlyInTheWriteRequest() {
        assertConnectionUpdate("replacement-synthetic-secret", "\"replacement-synthetic-secret\"");
    }

    private void assertConnectionUpdate(String input, String expectedJson) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://core.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PrinterManagementService service = new PrinterManagementService(builder.build());
        UUID printerId = UUID.randomUUID();
        PrinterForm form = new PrinterForm();
        form.setAccessCode(input);
        server.expect(requestTo("https://core.example/api/printers/" + printerId))
                .andExpect(method(HttpMethod.PATCH)).andRespond(withSuccess());
        server.expect(requestTo("https://core.example/api/printers/" + printerId + "/connection"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"accessCode\":" + expectedJson + "}", false))
                .andRespond(withSuccess());

        service.updatePrinter(printerId, form);

        server.verify();
    }
}
