package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.spon.edolhub.service.PrinterAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final RestTemplate restTemplate;
    private final PrinterAccessService printerAccessService;

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    @PostMapping("/request/skip-objects")
    public ResponseEntity<?> skipObjects(@RequestBody Map<String, Object> body) {
        String url = defaultPrinterUrl("/commands/skip-objects");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        return ResponseEntity
                .status(response.getStatusCode())
                .body(response.getBody());
    }

    @PostMapping("/request/pause")
    public ResponseEntity<?> pausePrint() {
        try {
            return restTemplate.exchange(
                    defaultPrinterUrl("/commands/pause"),
                    HttpMethod.POST,
                    null,
                    String.class
            );
        } catch (RestClientException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("Printer service unavailable");
        }
    }

    @PostMapping("/request/resume")
    public ResponseEntity<?> resumePrint() {
        try {
            return restTemplate.exchange(
                    defaultPrinterUrl("/commands/resume"),
                    HttpMethod.POST,
                    null,
                    String.class
            );
        } catch (RestClientException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("Printer service unavailable");
        }
    }

    @PostMapping("/request/stop")
    public ResponseEntity<?> stopPrint() {
        try {
            return restTemplate.exchange(
                    defaultPrinterUrl("/commands/stop"),
                    HttpMethod.POST,
                    null,
                    String.class
            );
        } catch (RestClientException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body("Printer service unavailable");
        }
    }

    @GetMapping("/modeltopimage")
    public ResponseEntity<byte[]> proxyTopImage() {
        String url = defaultPrinterUrl("/media/model/top");

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
        );

        return ResponseEntity
                .status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    @GetMapping("/modelimage")
    public ResponseEntity<byte[]> proxyModelImage() {
        String url = defaultPrinterUrl("/media/model/plate");

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );

            return ResponseEntity
                    .status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());
        } catch (RestClientException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/printers/{printerId}/modelimage")
    public ResponseEntity<byte[]> proxyPrinterModelImage(
            @PathVariable UUID printerId
    ) {
        printerAccessService.getPrinter(printerId);
        String url =
                edolCoreUrl +
                        "/api/printers/" +
                        printerId +
                        "/media/model/plate";

        try {
            ResponseEntity<byte[]> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            null,
                            byte[].class
                    );

            return ResponseEntity
                    .status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());

        } catch (RestClientException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String defaultPrinterUrl(String path) {
        return edolCoreUrl + "/api/printers/" + printerAccessService.getDefaultPrinter().getId() + path;
    }

}
