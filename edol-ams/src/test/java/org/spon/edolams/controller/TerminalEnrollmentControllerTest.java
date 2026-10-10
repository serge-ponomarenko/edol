package org.spon.edolams.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolams.service.InvalidTerminalPairingException;
import org.spon.edolams.service.TerminalEnrollmentRateLimiter;
import org.spon.edolams.service.TerminalEnrollmentService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalEnrollmentControllerTest {

    private static final UUID PRINTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000091");

    @Mock
    private TerminalEnrollmentService enrollmentService;

    @Mock
    private TerminalEnrollmentRateLimiter rateLimiter;

    @Mock
    private HttpServletRequest servletRequest;

    @Test
    void returnsTheCredentialOnceWithNoStoreAfterAValidPairing() {
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.91");
        when(enrollmentService.enroll(PRINTER_ID, "ABCDEFGHJK")).thenReturn(
                new TerminalEnrollmentService.EnrollmentResult(UUID.randomUUID(), UUID.randomUUID(), PRINTER_ID, "secret")
        );

        var response = controller().enroll(new TerminalEnrollmentController.EnrollmentRequest(PRINTER_ID, "ABCDEFGHJK"), servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().secret()).isEqualTo("secret");
        verify(rateLimiter).succeeded("192.0.2.91");
    }

    @Test
    void rejectsInvalidPairingWithoutLeakingAReasonAndCountsTheFailure() {
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.92");
        when(enrollmentService.enroll(PRINTER_ID, "ABCDEFGHJK")).thenThrow(new InvalidTerminalPairingException());

        var response = controller().enroll(new TerminalEnrollmentController.EnrollmentRequest(PRINTER_ID, "ABCDEFGHJK"), servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNull();
        verify(rateLimiter).failed("192.0.2.92");
    }

    @Test
    void blocksTheSourceBeforeCallingEnrollment() {
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.93");
        when(rateLimiter.isBlocked("192.0.2.93")).thenReturn(true);

        var response = controller().enroll(new TerminalEnrollmentController.EnrollmentRequest(PRINTER_ID, "ABCDEFGHJK"), servletRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(enrollmentService, never()).enroll(PRINTER_ID, "ABCDEFGHJK");
    }

    private TerminalEnrollmentController controller() {
        return new TerminalEnrollmentController(enrollmentService, rateLimiter);
    }
}
