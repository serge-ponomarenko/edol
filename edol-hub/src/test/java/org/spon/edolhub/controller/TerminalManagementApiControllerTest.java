package org.spon.edolhub.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolhub.service.AmsTerminalManagementClient;
import org.spon.edolhub.service.AmsTerminalManagementConflictException;
import org.spon.edolhub.service.PrinterAccessService;
import org.spon.edolhub.service.TenantOwnerAuthorizationService;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalManagementApiControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock
    private TenantOwnerAuthorizationService ownerAuthorizationService;

    @Mock
    private PrinterAccessService printerAccessService;

    @Mock
    private AmsTerminalManagementClient terminalManagementClient;

    @Test
    void createsPairingOnlyAfterOwnerAndPrinterAuthorization() {
        AmsTerminalManagementClient.Pairing pairing = new AmsTerminalManagementClient.Pairing(
                UUID.randomUUID(), PRINTER_ID, "ABCDEFGHJK", Instant.parse("2026-10-10T12:00:00Z")
        );
        when(terminalManagementClient.createPairing(PRINTER_ID)).thenReturn(pairing);

        var response = controller().createPairing(PRINTER_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().pairingCode()).isEqualTo("ABCDEFGHJK");
        InOrder order = inOrder(ownerAuthorizationService, printerAccessService, terminalManagementClient);
        order.verify(ownerAuthorizationService).requireCurrentOwner();
        order.verify(printerAccessService).getPrinter(PRINTER_ID);
        order.verify(terminalManagementClient).createPairing(PRINTER_ID);
    }

    @Test
    void doesNotCallAmsWhenOwnerAuthorizationFails() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("owner required"))
                .when(ownerAuthorizationService).requireCurrentOwner();

        assertThatThrownBy(() -> controller().revoke(PRINTER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(printerAccessService, never()).getPrinter(PRINTER_ID);
        verify(terminalManagementClient, never()).revoke(PRINTER_ID);
    }

    @Test
    void returnsTheOwnerScopedTerminalStatus() {
        AmsTerminalManagementClient.TerminalStatus status = new AmsTerminalManagementClient.TerminalStatus(
                UUID.randomUUID(), "ACTIVE", Instant.parse("2026-10-10T12:00:00Z"),
                Instant.parse("2026-10-10T12:01:00Z"), null, null, null, null, null, null
        );
        when(terminalManagementClient.status(PRINTER_ID)).thenReturn(Optional.of(status));

        var response = controller().status(PRINTER_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(status);
        InOrder order = inOrder(ownerAuthorizationService, printerAccessService, terminalManagementClient);
        order.verify(ownerAuthorizationService).requireCurrentOwner();
        order.verify(printerAccessService).getPrinter(PRINTER_ID);
        order.verify(terminalManagementClient).status(PRINTER_ID);
    }

    @Test
    void mapsExpectedAmsPairingConflictsToHttp409() {
        when(terminalManagementClient.createPairing(PRINTER_ID))
                .thenThrow(new AmsTerminalManagementConflictException("already active"));

        assertThatThrownBy(() -> controller().createPairing(PRINTER_ID))
                .isInstanceOf(AmsTerminalManagementConflictException.class);
        assertThat(controller().conflict().getStatusCode().value()).isEqualTo(409);
    }

    private TerminalManagementApiController controller() {
        return new TerminalManagementApiController(ownerAuthorizationService, printerAccessService, terminalManagementClient);
    }
}
