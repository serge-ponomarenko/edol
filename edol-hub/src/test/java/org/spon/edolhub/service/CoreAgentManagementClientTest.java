package org.spon.edolhub.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CoreAgentManagementClientTest {
    @Test
    void preservesPrinterScopedPathsAndDecodesOnlySafeStatusOrOneTimeCode() {
        var builder = RestClient.builder().baseUrl("https://core.example");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new CoreAgentManagementClient(builder.build());
        UUID printer = UUID.randomUUID();
        UUID agent = UUID.randomUUID();
        String base = "https://core.example/api/printers/" + printer + "/agent";
        server.expect(requestTo(base + "/pairings")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"agentId\":\"" + agent + "\",\"pairingCode\":\"0123456789AB\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(base)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(base + "/revoke")).andExpect(method(HttpMethod.POST)).andRespond(withNoContent());
        assertThat(client.pair(printer, false).agentId()).isEqualTo(agent);
        assertThat(client.status(printer)).isNull();
        client.revoke(printer);
        server.verify();
    }
}
