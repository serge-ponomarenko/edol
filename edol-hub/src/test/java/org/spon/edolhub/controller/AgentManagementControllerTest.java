package org.spon.edolhub.controller;

import org.junit.jupiter.api.Test;
import org.spon.edolhub.service.CoreAgentManagementClient;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentManagementControllerTest {
    private final TenantOwnerAuthorizationService owners = mock(TenantOwnerAuthorizationService.class);
    private final PrinterAccessService printers = mock(PrinterAccessService.class);
    private final CoreAgentManagementClient agents = mock(CoreAgentManagementClient.class);
    private final AgentManagementController controller = new AgentManagementController(owners, printers, agents);
    private final UUID printer = UUID.randomUUID();

    @Test
    void returnsCodeOnlyAfterOwnerAndPrinterChecksWithoutSessionOrRedirectStorage() {
        var pairing = new CoreAgentManagementClient.Pairing(UUID.randomUUID(), "0123456789AB", Instant.now());
        when(agents.pair(printer, false)).thenReturn(pairing);
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();
        assertThat(controller.pair(printer, model, response)).isEqualTo("dashboard/printers/agent");
        assertThat(model.get("agentPairing")).isSameAs(pairing);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        var order = inOrder(owners, printers, agents);
        order.verify(owners).requireCurrentOwner();
        order.verify(printers).getPrinter(printer);
        order.verify(agents).pair(printer, false);
        var getModel = new ExtendedModelMap();
        controller.page(printer, getModel, new MockHttpServletResponse());
        assertThat(getModel).doesNotContainKey("agentPairing");
        assertThat(pairing.toString()).doesNotContain("0123456789AB");
    }

    @Test
    void ownerDenialPrecedesPrinterLookupAndRemoteCalls() {
        doThrow(new AccessDeniedException("Owner required")).when(owners).requireCurrentOwner();
        assertThatThrownBy(() -> controller.pair(printer, new ExtendedModelMap(), new MockHttpServletResponse()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(printers, agents);
    }

    @Test
    void foreignPrinterCannotRotateOrRevokeAnAgent() {
        when(printers.getPrinter(printer)).thenThrow(new IllegalArgumentException("unavailable"));
        assertThatThrownBy(() -> controller.rotate(printer, new ExtendedModelMap(), new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> controller.revoke(printer, new ExtendedModelMap(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(agents);
    }

    @Test
    void exposesSafeConflictAndSupportsReplacementAndRevocation() {
        when(agents.pair(printer, false)).thenThrow(org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.CONFLICT, "Conflict", org.springframework.http.HttpHeaders.EMPTY,
                new byte[0], null));
        var model = new ExtendedModelMap();
        var response = new MockHttpServletResponse();
        controller.pair(printer, model, response);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(model).containsKey("agentError").doesNotContainKey("agentPairing");
        controller.rotate(printer, new ExtendedModelMap(), new MockHttpServletResponse());
        verify(agents).pair(printer, true);
        controller.revoke(printer, new ExtendedModelMap(), new MockHttpServletResponse());
        verify(agents).revoke(printer);
    }
}
