package org.spon.edolcore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.service.agent.pairing.AgentEnrollmentLimiter;
import org.spon.edolcore.service.agent.pairing.AgentPairingService;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.upload.BoundedUpload;
import org.spon.edolcore.service.upload.ModelFileStorage;
import org.spon.edolcore.service.upload.SnapshotValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RestController
@Slf4j
@RequestMapping("/api/agent/v1")
@ConditionalOnExpression("'${edol.deployment.mode:}' == 'secure-multi-tenant' && ${edol-core.agent.enabled:false}")
public class SecureAgentController {
    private final AgentPairingService agents;
    private final AgentEnrollmentLimiter limiter;
    private final ModelFileStorage files;
    private final SnapshotValidator images;
    private final CameraSnapshotStore snapshots;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    public SecureAgentController(AgentPairingService agents, AgentEnrollmentLimiter limiter, ModelFileStorage files,
                                 SnapshotValidator images, CameraSnapshotStore snapshots,
                                 ApplicationEventPublisher events, ObjectMapper mapper) {
        this.agents = agents;
        this.limiter = limiter;
        this.files = files;
        this.images = images;
        this.snapshots = snapshots;
        this.events = events;
        this.mapper = mapper;
    }

    @PostMapping(value = "/enroll", consumes = "application/json")
    public ResponseEntity<AgentPairingService.Enrollment> enroll(HttpServletRequest request) throws IOException {
        if (!limiter.acquire(request.getRemoteAddr())) {
            return ResponseEntity.status(429).build();
        }
        var bytes = new ByteArrayOutputStream();
        BoundedUpload.copy(request.getInputStream(), bytes, 256);
        EnrollmentRequest enrollment;
        try {
            enrollment = mapper.readValue(bytes.toByteArray(), EnrollmentRequest.class);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid enrollment request");
        }
        if (enrollment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid enrollment request");
        }
        return ResponseEntity.ok(agents.enroll(enrollment.pairingCode()));
    }

    @PostMapping(value = "/upload", consumes = "application/octet-stream")
    public ResponseEntity<Void> upload(@AuthenticationPrincipal AgentPairingService.Identity identity,
                                      @RequestParam String fileName, InputStream body) throws IOException {
        ModelFileStorage.StoredModel model;
        try {
            model = files.store(identity.printerId(), fileName, body);
        } catch (IOException exception) {
            log.warn("Secure agent model stream/storage failed: agentId={}, printerId={}, failureType={}",
                    identity.agentId(), identity.printerId(), exception.getClass().getSimpleName());
            throw exception;
        }
        events.publishEvent(new ModelAvailableEvent(identity.printerId(), model.path()));
        log.info("Secure agent model accepted: agentId={}, printerId={}, bytes={}",
                identity.agentId(), identity.printerId(), model.bytes());
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/camera/snapshot", consumes = "image/jpeg")
    public ResponseEntity<Void> snapshot(@AuthenticationPrincipal AgentPairingService.Identity identity,
                                        InputStream body) throws IOException {
        byte[] image = images.read(body);
        snapshots.store(identity.printerId(), image);
        log.debug("Secure agent snapshot accepted: agentId={}, printerId={}, bytes={}",
                identity.agentId(), identity.printerId(), image.length);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler({ResponseStatusException.class, IOException.class})
    ResponseEntity<Void> rejected(Exception exception) {
        int status = exception instanceof ResponseStatusException rejection ? rejection.getStatusCode().value() : 500;
        log.warn("Secure agent request rejected: status={}, failureType={}", status, exception.getClass().getSimpleName());
        return ResponseEntity.status(status).build();
    }

    public record EnrollmentRequest(String pairingCode) {
        @Override public String toString() { return "AgentEnrollmentRequest[REDACTED]"; }
    }
}
