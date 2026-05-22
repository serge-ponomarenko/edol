package org.spon.edolhub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
public class ProxyController {

    private final RestTemplate restTemplate;

    @Value("${edol-core.url}")
    private String edolCoreUrl;

    @PostMapping("/request/skip-objects")
    public ResponseEntity<?> skipObjects(@RequestBody Map<String, Object> body) {
        String url = edolCoreUrl + "/printer/request/skip-objects";

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
                    edolCoreUrl + "/printer/request/pause",
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
                    edolCoreUrl + "/printer/request/resume",
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
                    edolCoreUrl + "/printer/request/stop",
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
        String url = edolCoreUrl + "/printer/modeltopimage";

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
        String url = edolCoreUrl + "/printer/modelimage";

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

    @GetMapping("/camera")
    public ResponseEntity<byte[]> proxyCameraImage() {
        String url = edolCoreUrl + "/camera/latest";

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

}
