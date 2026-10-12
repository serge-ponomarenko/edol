package org.spon.edolams.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spon.edolams.controller.TerminalEnrollmentController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TerminalEnrollmentRateLimitHttpContractTest {

    @Mock
    private TerminalEnrollmentService enrollmentService;

    @Mock
    private HttpServletRequest servletRequest;

    @Test
    void returnsTooManyRequestsAfterFiveInvalidAttemptsFromTheSameSource() {
        TerminalEnrollmentController controller = new TerminalEnrollmentController(
                enrollmentService,
                new TerminalEnrollmentRateLimiter()
        );
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.94");
        when(enrollmentService.enroll("ABCDEFGHIJK")).thenThrow(new InvalidTerminalPairingException());

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(controller.enroll(
                    new TerminalEnrollmentController.EnrollmentRequest("ABCDEFGHIJK"), servletRequest
            ).getStatusCode().value()).isEqualTo(401);
        }

        assertThat(controller.enroll(
                new TerminalEnrollmentController.EnrollmentRequest("ABCDEFGHIJK"), servletRequest
        ).getStatusCode().value()).isEqualTo(429);
        verify(enrollmentService, times(5)).enroll("ABCDEFGHIJK");
    }
}
