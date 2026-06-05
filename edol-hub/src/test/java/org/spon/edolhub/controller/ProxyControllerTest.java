package org.spon.edolhub.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProxyController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "edolCoreUrl", "http://edolcore:8080");
    }

    @Nested
    @DisplayName("skipObjects")
    class SkipObjects {

        @Test
        @DisplayName("proxies skip-objects request to core")
        void proxiesRequest() {
            Map<String, Object> body = Map.of("obj", "test");
            ResponseEntity<String> coreResponse = ResponseEntity.ok("done");
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/skip-objects"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(coreResponse);

            ResponseEntity<?> response = controller.skipObjects(body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("done");
        }
    }

    @Nested
    @DisplayName("pausePrint")
    class PausePrint {

        @Test
        @DisplayName("proxies pause request")
        void proxiesPause() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/pause"),
                    eq(HttpMethod.POST),
                    isNull(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("paused"));

            ResponseEntity<?> response = controller.pausePrint();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("returns 502 when core unavailable")
        void returnsBadGateway() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/pause"),
                    eq(HttpMethod.POST),
                    isNull(),
                    eq(String.class)
            )).thenThrow(new RestClientException("Connection refused"));

            ResponseEntity<?> response = controller.pausePrint();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isEqualTo("Printer service unavailable");
        }
    }

    @Nested
    @DisplayName("resumePrint")
    class ResumePrint {

        @Test
        @DisplayName("proxies resume request")
        void proxiesResume() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/resume"),
                    eq(HttpMethod.POST),
                    isNull(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("resumed"));

            ResponseEntity<?> response = controller.resumePrint();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("returns 502 when core unavailable")
        void returnsBadGateway() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/resume"),
                    eq(HttpMethod.POST),
                    isNull(),
                    eq(String.class)
            )).thenThrow(new RestClientException("Connection refused"));

            ResponseEntity<?> response = controller.resumePrint();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isEqualTo("Printer service unavailable");
        }
    }

    @Nested
    @DisplayName("stopPrint")
    class StopPrint {

        @Test
        @DisplayName("proxies stop request")
        void proxiesStop() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/stop"),
                    eq(HttpMethod.POST),
                    isNull(),
                    eq(String.class)
            )).thenReturn(ResponseEntity.ok("stopped"));

            ResponseEntity<?> response = controller.stopPrint();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("returns 502 when core unavailable")
        void returnsBadGateway() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/request/stop"),
                    eq(HttpMethod.POST),
                    isNull(),
                    eq(String.class)
            )).thenThrow(new RestClientException("Connection refused"));

            ResponseEntity<?> response = controller.stopPrint();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody()).isEqualTo("Printer service unavailable");
        }
    }

    @Nested
    @DisplayName("proxyTopImage")
    class ProxyTopImage {

        @Test
        @DisplayName("proxies model top image")
        void proxiesTopImage() {
            byte[] imageData = "image".getBytes();
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/modeltopimage"),
                    eq(HttpMethod.GET),
                    isNull(),
                    eq(byte[].class)
            )).thenReturn(ResponseEntity.ok(imageData));

            ResponseEntity<byte[]> response = controller.proxyTopImage();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(imageData);
        }
    }

    @Nested
    @DisplayName("proxyModelImage")
    class ProxyModelImage {

        @Test
        @DisplayName("proxies model image")
        void proxiesModelImage() {
            byte[] imageData = "model".getBytes();
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/modelimage"),
                    eq(HttpMethod.GET),
                    isNull(),
                    eq(byte[].class)
            )).thenReturn(ResponseEntity.ok(imageData));

            ResponseEntity<byte[]> response = controller.proxyModelImage();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(imageData);
        }

        @Test
        @DisplayName("returns 404 when core unavailable")
        void returnsNotFound() {
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/printer/modelimage"),
                    eq(HttpMethod.GET),
                    isNull(),
                    eq(byte[].class)
            )).thenThrow(new RestClientException("Connection refused"));

            ResponseEntity<byte[]> response = controller.proxyModelImage();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("proxyCameraImage")
    class ProxyCameraImage {

        @Test
        @DisplayName("proxies camera image")
        void proxiesCameraImage() {
            byte[] imageData = "camera".getBytes();
            when(restTemplate.exchange(
                    eq("http://edolcore:8080/camera/latest"),
                    eq(HttpMethod.GET),
                    isNull(),
                    eq(byte[].class)
            )).thenReturn(ResponseEntity.ok(imageData));

            ResponseEntity<byte[]> response = controller.proxyCameraImage();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(imageData);
        }
    }
}
