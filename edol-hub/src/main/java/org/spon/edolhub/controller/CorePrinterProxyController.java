package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/printers/{printerId}")
@RequiredArgsConstructor
public class CorePrinterProxyController {

    private static final Set<String> COMMANDS = Set.of(
            "skip-objects",
            "spool-change",
            "pause",
            "resume",
            "stop",
            "print-speed",
            "pushall",
            "fetchmetadata"
    );

    private final RestTemplate restTemplate;
    private final PrinterAccessService printerAccessService;

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    @PostMapping("/commands/{command}")
    public ResponseEntity<String> command(
            @PathVariable UUID printerId,
            @PathVariable String command,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        printerAccessService.getPrinter(printerId);
        if (!COMMANDS.contains(command)) {
            return ResponseEntity.badRequest().body("Unsupported printer command");
        }

        return restTemplate.exchange(
                edolCoreUrl + "/api/printers/" + printerId + "/commands/" + command,
                HttpMethod.POST,
                body == null ? null : new org.springframework.http.HttpEntity<>(body),
                String.class
        );
    }

    @GetMapping("/media/model/{image}")
    public ResponseEntity<byte[]> modelImage(
            @PathVariable UUID printerId,
            @PathVariable String image
    ) {
        printerAccessService.getPrinter(printerId);
        if (!Set.of("plate", "top").contains(image)) {
            return ResponseEntity.badRequest().build();
        }
        return restTemplate.exchange(
                edolCoreUrl + "/api/printers/" + printerId + "/media/model/" + image,
                HttpMethod.GET,
                null,
                byte[].class
        );
    }

    @GetMapping("/camera/snapshot")
    public ResponseEntity<byte[]> camera(@PathVariable UUID printerId) {
        printerAccessService.getPrinter(printerId);
        return restTemplate.exchange(
                edolCoreUrl + "/api/printers/" + printerId + "/camera/snapshot",
                HttpMethod.GET,
                null,
                byte[].class
        );
    }
}
