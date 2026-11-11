package org.spon.edolams.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClientResponseException;
import org.spon.edol.deployment.DeploymentMode;
import org.spon.edol.mqtt.CoreMqttIngressObservation;
import org.spon.edolams.config.AmsPrinterTenantProperties;
import org.spon.edolams.service.AmsPrinterTenantResolver;
import org.spon.edolams.service.AmsSpoolChangerService;
import org.spon.edolams.service.AmsTenantContext;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

@ExtendWith(OutputCaptureExtension.class)
class MqttEventListenerTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID printerId = UUID.randomUUID();
    private final AmsSpoolChangerService changer = mock(AmsSpoolChangerService.class);
    private final AmsTenantContext tenants = new AmsTenantContext();

    @Test
    void acceptsV2AndV3AndUsesTheirPayloadOnlyInsideTheMappedTenantScope() {
        var listener = secureListener();
        doAnswer(invocation -> {
            assertThat(tenants.currentTenantId()).isEqualTo(tenantId);
            return null;
        }).when(changer).setAmsSpoolIntoSlot(printerId, 2);

        listener.handle(new GenericMessage<>(envelope(2, tenantId).toString()));
        listener.handle(new GenericMessage<>(envelope(3, tenantId).toString()));

        verify(changer, times(2)).setAmsSpoolIntoSlot(printerId, 2);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
        var counts = observation(listener).snapshot();
        assertThat(counts.validatedV2()).isEqualTo(1);
        assertThat(counts.validatedV3()).isEqualTo(1);
        assertThat(counts.withLegacyEvent()).isEqualTo(1);
    }

    @Test
    void rejectsUnsupportedAndContradictoryEnvelopesBeforeTenantResolutionOrMutation() {
        var listener = secureListener();
        ObjectNode missingLegacy = envelope(2, tenantId);
        missingLegacy.remove("event");
        ObjectNode mismatchedLegacy = envelope(3, tenantId).put("event", "ams.slot.unloaded");
        ObjectNode fractionalVersion = envelope(2, tenantId).put("schemaVersion", 2.5);
        for (String invalid : List.of(missingLegacy.toString(), mismatchedLegacy.toString(),
                fractionalVersion.toString(), envelope(4, tenantId).toString(), "{malformed}", "null")) {
            listener.handle(new GenericMessage<>(invalid));
        }
        verifyNoInteractions(changer);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
        assertThat(observation(listener).snapshot().rejectedEnvelope()).isEqualTo(6);
        assertThat(observation(listener).snapshot().processingFailures()).isZero();
    }

    @Test
    void rejectsCrossTenantAndUnknownPrinterBeforeSpoolMutation() {
        var listener = secureListener();
        listener.handle(new GenericMessage<>(envelope(3, UUID.randomUUID()).toString()));
        listener.handle(new GenericMessage<>(envelope(3, tenantId)
                .put("printerId", UUID.randomUUID().toString()).toString()));
        verifyNoInteractions(changer);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(2);
    }

    @Test
    void mappedMirrorCannotConsumeAnotherPrinterStagingAndMappingFailuresRemainRejected() {
        UUID agentPrinter = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://hub.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmsSpoolChangerService realChanger = new AmsSpoolChangerService(builder.build());
        var resolver = new AmsPrinterTenantResolver(DeploymentMode.SECURE_MULTI_TENANT,
                new AmsPrinterTenantProperties(List.of(
                        new AmsPrinterTenantProperties.PrinterTenant(printerId, tenantId),
                        new AmsPrinterTenantProperties.PrinterTenant(agentPrinter, tenantId))),
                tenants);
        ReflectionTestUtils.invokeMethod(resolver, "validateMappings");
        var listener = new MqttEventListener(realChanger, DeploymentMode.SECURE_MULTI_TENANT, resolver);
        AtomicInteger writes = new AtomicInteger();
        server.expect(requestTo("https://hub.test/s/" + printerId + "/41/2"))
                .andExpect(method(POST))
                .andExpect(request -> {
                    assertThat(tenants.currentTenantId()).isEqualTo(tenantId);
                    writes.incrementAndGet();
                })
                .andRespond(withNoContent());
        realChanger.setSpoolScannedState(printerId, 41L);

        listener.handle(new GenericMessage<>(envelope(3, tenantId)
                .put("printerId", agentPrinter.toString()).toString()));
        assertThat(writes.get()).isZero();
        assertThat(observation(listener).snapshot().processingFailures()).isZero();

        listener.handle(new GenericMessage<>(envelope(3, UUID.randomUUID()).toString()));
        listener.handle(new GenericMessage<>(envelope(3, tenantId)
                .put("printerId", UUID.randomUUID().toString()).toString()));
        assertThat(writes.get()).isZero();
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(2);

        listener.handle(new GenericMessage<>(envelope(3, tenantId).toString()));
        assertThat(writes.get()).isEqualTo(1);
        listener.handle(new GenericMessage<>(envelope(3, tenantId).toString()));
        assertThat(writes.get()).isEqualTo(1);
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(2);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    void preservesHomeLegacyPayloadWithoutSecureMappingsOrObservationRequirements() {
        var listener = new MqttEventListener(changer, DeploymentMode.HOME, null);
        ObjectNode legacy = new ObjectMapper().createObjectNode()
                .put("event", "ams.slot.loaded").put("printerId", printerId.toString()).put("slot", 2);
        listener.handle(new GenericMessage<>(legacy.toString()));
        verify(changer).setAmsSpoolIntoSlot(printerId, 2);
        assertThat(observation(listener).snapshot().validatedV2()).isZero();
        assertThat(observation(listener).snapshot().rejectedEnvelope()).isZero();
    }

    @Test
    void reportsMappingFailuresSeparatelyWithoutWeakeningIsolation(CapturedOutput output) {
        var listener = secureListener();
        listener.handle(new GenericMessage<>(envelope(3, UUID.randomUUID()).toString()));
        listener.handle(new GenericMessage<>(envelope(3, tenantId)
                .put("printerId", UUID.randomUUID().toString()).toString()));

        assertThat(output.getOut()).contains("stage=tenant_mapping", "reason=unmapped_printer",
                "category=IllegalArgumentException", "category=UnmappedAmsPrinterException", "type=ams.slot.loaded");
        verifyNoInteractions(changer);
        assertThat(observation(listener).snapshot().rejectedEnvelope()).isZero();
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(2);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsHandlerAndPersistenceCategoryWithoutExceptionMessagesOrPayload(CapturedOutput output) {
        var listener = secureListener();
        doThrow(new IllegalStateException("secret-outer", new IllegalArgumentException("secret-sql")))
                .when(changer).setAmsSpoolIntoSlot(printerId, 2);
        var json = envelope(3, tenantId);
        json.withObject("payload").put("secret", "secret-body");
        listener.handle(new GenericMessage<>(json.toString()));

        assertThat(output.getOut()).contains("stage=handler", "category=IllegalStateException",
                "cause=IllegalArgumentException", "printer=" + printerId, "tenant=" + tenantId)
                .doesNotContain("secret-outer", "secret-sql", "secret-body");
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(1);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsMissingSlotAsHandlerFailureAndDoesNotMutateSpools(CapturedOutput output) {
        var listener = secureListener();
        var json = envelope(3, tenantId);
        json.withObject("payload").remove("slot");
        listener.handle(new GenericMessage<>(json.toString()));

        assertThat(output.getOut()).contains("stage=handler", "category=NullPointerException");
        verifyNoInteractions(changer);
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(1);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reportsUpstreamStatusWithoutItsBody(CapturedOutput output) {
        var listener = secureListener();
        doThrow(new RestClientResponseException("secret-message", 403, "Forbidden", null,
                "secret-upstream-body".getBytes(java.nio.charset.StandardCharsets.UTF_8), null))
                .when(changer).setAmsSpoolIntoSlot(printerId, 2);
        listener.handle(new GenericMessage<>(envelope(3, tenantId).toString()));

        assertThat(output.getOut()).contains("stage=handler", "http_status=403")
                .doesNotContain("secret-message", "secret-upstream-body");
        assertThat(observation(listener).snapshot().processingFailures()).isEqualTo(1);
        assertThatThrownBy(tenants::currentTenantId).isInstanceOf(IllegalStateException.class);
    }

    private MqttEventListener secureListener() {
        var resolver = new AmsPrinterTenantResolver(DeploymentMode.SECURE_MULTI_TENANT,
                new AmsPrinterTenantProperties(List.of(new AmsPrinterTenantProperties.PrinterTenant(printerId, tenantId))),
                tenants);
        ReflectionTestUtils.invokeMethod(resolver, "validateMappings");
        return new MqttEventListener(changer, DeploymentMode.SECURE_MULTI_TENANT, resolver);
    }

    private ObjectNode envelope(int version, UUID tenant) {
        ObjectNode json = new ObjectMapper().createObjectNode().put("schemaVersion", version)
                .put("eventId", UUID.randomUUID().toString()).put("eventType", "ams.slot.loaded")
                .put("tenantId", tenant.toString()).put("printerId", printerId.toString())
                .put("timestamp", "2026-10-21T12:00:00Z");
        json.putObject("payload").put("slot", 2);
        if (version == 2) {
            json.put("event", "ams.slot.loaded").put("slot", 2);
        }
        return json;
    }

    private CoreMqttIngressObservation observation(MqttEventListener listener) {
        return (CoreMqttIngressObservation) ReflectionTestUtils.getField(listener, "ingress");
    }
}
