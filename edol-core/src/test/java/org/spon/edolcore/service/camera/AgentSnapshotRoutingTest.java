package org.spon.edolcore.service.camera;

import org.junit.jupiter.api.Test;
import org.spon.edolcore.persistence.printer.*;
import org.spon.edolcore.service.agent.command.*;
import org.spon.edolcore.service.printer.management.PrinterManagementService;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentSnapshotRoutingTest {
    private final PrinterManagementService printers = mock(PrinterManagementService.class);
    private final AgentCommandPublisher publisher = mock(AgentCommandPublisher.class);
    private final DefaultAgentCommandGateway gateway = new DefaultAgentCommandGateway(publisher, printers);

    @Test
    void directLifecycleDoesNotPublishAnyAgentSnapshotCommand() {
        UUID id = configured(PrinterConnectionMode.DIRECT);
        gateway.disableSnapshotScheduler(id);
        gateway.enableSnapshotScheduler(id);
        gateway.uploadSnapshot(id);
        verifyNoInteractions(publisher);
    }

    @Test
    void agentCameraRequestsAnAsynchronousSnapshotWithoutMetadataOrOverwritingStoredFrames() throws Exception {
        UUID id = configured(PrinterConnectionMode.AGENT);
        var agent = new AgentCameraProvider(gateway);
        var cameras = new DefaultCameraProvider(printers, mock(LegacyCameraProvider.class), mock(RtspsCameraProvider.class), agent);
        assertThat(cameras.supports(id)).isTrue();
        assertThat(cameras.capture(id)).isNull();
        verify(publisher).publishSnapshotIfAvailable(id, "{\"command\":\"UPLOAD_SNAPSHOT\"}");
    }

    @Test
    void unavailableAgentAuthorityAndForeignPrinterStillFailClosed() {
        UUID id = configured(PrinterConnectionMode.AGENT);
        gateway.uploadSnapshot(id);
        verify(publisher).publishSnapshotIfAvailable(eq(id), anyString());
        UUID foreign = UUID.randomUUID();
        when(printers.getPrinter(foreign)).thenThrow(new IllegalArgumentException("Unavailable printer"));
        assertThatThrownBy(() -> gateway.disableSnapshotScheduler(foreign)).isInstanceOf(IllegalArgumentException.class);
        verify(publisher, never()).publish(eq(foreign), anyString());
    }

    private UUID configured(PrinterConnectionMode mode) {
        UUID id = UUID.randomUUID();
        var printer = new Printer();
        printer.setConnectionMode(mode);
        when(printers.getPrinter(id)).thenReturn(printer);
        return id;
    }
}
