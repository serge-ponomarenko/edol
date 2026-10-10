package org.spon.edolams.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.spon.edolams.service.InvalidTerminalPairingException;
import org.spon.edolams.service.TerminalEnrollmentRateLimiter;
import org.spon.edolams.service.TerminalEnrollmentService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.UUID;

@RestController
@RequestMapping("/api/terminal/v1")
@ConditionalOnProperty(name = "edol.deployment.mode", havingValue = "secure-multi-tenant")
public class TerminalEnrollmentController {

    private final TerminalEnrollmentService enrollmentService;
    private final TerminalEnrollmentRateLimiter rateLimiter;

    public TerminalEnrollmentController(
            TerminalEnrollmentService enrollmentService,
            TerminalEnrollmentRateLimiter rateLimiter
    ) {
        this.enrollmentService = enrollmentService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/enroll")
    public ResponseEntity<EnrollmentResponse> enroll(
            @Valid @RequestBody EnrollmentRequest request,
            HttpServletRequest servletRequest
    ) {
        String source = servletRequest.getRemoteAddr();
        if (rateLimiter.isBlocked(source)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        try {
            TerminalEnrollmentService.EnrollmentResult result = enrollmentService.enroll(
                    request.printerId(), request.pairingCode()
            );
            rateLimiter.succeeded(source);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new EnrollmentResponse(result.terminalId(), result.secret()));
        } catch (InvalidTerminalPairingException exception) {
            rateLimiter.failed(source);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
    }

    public record EnrollmentRequest(@NotNull UUID printerId, @NotBlank String pairingCode) {
    }

    public record EnrollmentResponse(UUID terminalId, String secret) {
    }
}
