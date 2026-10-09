package org.spon.edolams.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edolams.config.AmsPrinterTenantProperties;
import org.spon.edolams.model.HubFilamentSpool;
import org.spon.edolams.service.AmsPrinterTenantResolver;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.spon.edolams.service.AmsTenantContext;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.http.HttpMethod.POST;

class SpoolControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsHubSpoolPayloadWithFieldsOutsideTheAmsContract() throws Exception {
        HubFilamentSpool hubSpool = objectMapper.readValue("""
                {
                  "id": 1,
                  "weightRemaining": 1000.0,
                  "emptySpoolWeight": 250.0,
                  "purchasedAt": "2026-10-09T10:00:00",
                  "status": "SEALED",
                  "filament": {
                    "brand": "Basic",
                    "colorHex": "#AABBCC",
                    "tenantId": "00000000-0000-0000-0000-000000000001",
                    "vendor": {"name": "Bambu Lab", "tenantId": "00000000-0000-0000-0000-000000000001"},
                    "materialType": {"name": "PLA", "tenantId": "00000000-0000-0000-0000-000000000001"}
                  }
                }
                """, HubFilamentSpool.class);

        var spool = SpoolController.toAmsSpool(hubSpool);

        assertThat(spool.getSpoolId()).isEqualTo(1L);
        assertThat(spool.getBrand()).isEqualTo("Basic");
        assertThat(spool.getColor()).isEqualTo("#AABBCC");
        assertThat(spool.getMaterial()).isEqualTo("PLA");
        assertThat(spool.getVendor()).isEqualTo("Bambu Lab");
        assertThat(spool.getRemaining()).isEqualTo(1000);
    }

    @Test
    void sendsSpoolChangeOnlyToTheHubClient() {
        UUID printerId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://hub.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmsSpoolChangerService changerService = mock(AmsSpoolChangerService.class);
        SpoolController controller = new SpoolController(
                RestClient.create(),
                builder.build(),
                changerService,
                homeTenantResolver()
        );

        server.expect(requestTo("https://hub.test/s/" + printerId + "/1/2"))
                .andExpect(method(POST))
                .andRespond(withNoContent());

        var response = controller.setSpoolToAms(1L, 2, printerId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(changerService).resetScannedSpool(printerId);
        server.verify();
    }

    private AmsPrinterTenantResolver homeTenantResolver() {
        return new AmsPrinterTenantResolver(
                DeploymentMode.HOME,
                new AmsPrinterTenantProperties(List.of()),
                new AmsTenantContext()
        );
    }
}
