package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AmsTerminalManagementClientTest {

    @Test
    void representsAnUnpairedTerminalAsEmptyAndDoesNotHideAuthenticationFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ams.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmsTerminalManagementClient client = new AmsTerminalManagementClient(builder.build());
        UUID printer = UUID.randomUUID();
        server.expect(requestTo("http://ams.test/internal/terminals/" + printer))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://ams.test/internal/terminals/" + printer))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThat(client.status(printer)).isEmpty();
        assertThatThrownBy(() -> client.status(printer))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.Unauthorized.class);
        server.verify();
    }

    @Test
    void mapsAReplacementLifecycleNotFoundToARecoverableConflict() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ams.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmsTerminalManagementClient client = new AmsTerminalManagementClient(builder.build());
        UUID printerId = UUID.fromString("00000000-0000-0000-0000-000000000082");
        server.expect(requestTo("http://ams.test/internal/terminals/replace"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.replace(printerId))
                .isInstanceOf(AmsTerminalManagementConflictException.class)
                .hasMessage("The terminal lifecycle changed. Refresh the printer page and try again.");

        server.verify();
    }
}
