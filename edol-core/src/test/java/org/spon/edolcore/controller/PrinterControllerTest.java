package org.spon.edolcore.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.PrinterStateService;
import org.spon.edolcore.service.printermqtt.BambuMqttClient;
import org.spon.edolcore.service.printmetadata.ModelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrinterController")
@MockitoSettings(strictness = Strictness.LENIENT)
class PrinterControllerTest {

    @Mock
    private PrinterStateService service;

    @Mock
    private BambuMqttClient client;

    @Mock
    private ModelService modelService;

    @InjectMocks
    private PrinterController controller;

    private PrinterState printerState;

    @BeforeEach
    void setUp() {
        printerState = new PrinterState();
        printerState.setPrinterId(1);
    }

    @Nested
    @DisplayName("getState")
    class GetState {

        @Test
        @DisplayName("returns state when connected")
        void returnsStateWhenConnected() {
            when(client.isConnected()).thenReturn(true);
            when(service.getState()).thenReturn(printerState);

            PrinterState result = controller.getState();

            assertThat(result.getPrinterId()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns offline state when disconnected")
        void returnsOfflineStateWhenDisconnected() {
            when(client.isConnected()).thenReturn(false);

            PrinterState result = controller.getState();

            assertThat(result.isOnline()).isFalse();
        }
    }

    @Nested
    @DisplayName("isConnected")
    class IsConnected {

        @Test
        @DisplayName("returns true when MQTT connected")
        void returnsTrueWhenConnected() {
            when(client.isConnected()).thenReturn(true);

            boolean result = controller.isConnected();

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("returns false when MQTT disconnected")
        void returnsFalseWhenDisconnected() {
            when(client.isConnected()).thenReturn(false);

            boolean result = controller.isConnected();

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getModelImage")
    class GetModelImage {

        @Test
        @DisplayName("returns 404 when metadata not loaded")
        void returns404WhenMetadataNotLoaded() {
            when(service.getState()).thenReturn(printerState);
            when(modelService.isMetadataLoaded()).thenReturn(false);

            ResponseEntity<?> response = controller.getModelImage();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getModelTopImage")
    class GetModelTopImage {

        @Test
        @DisplayName("returns 404 when metadata not loaded")
        void returns404WhenMetadataNotLoaded() {
            when(service.getState()).thenReturn(printerState);
            when(modelService.isMetadataLoaded()).thenReturn(false);

            ResponseEntity<?> response = controller.getModelTopImage();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("skipObjects")
    class SkipObjects {

        @Test
        @DisplayName("calls client.skipObjects and returns ok with skipped IDs")
        void skipsObjects() {
            var request = new PrinterController.SkipObjectsRequest();
            request.setObjectIds(List.of(1, 2, 3));

            ResponseEntity<?> response = controller.skipObjects(request);

            verify(client).skipObjects(List.of(1, 2, 3));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body)
                    .containsEntry("status", "ok")
                    .containsEntry("skipped", List.of(1, 2, 3));
        }
    }

    @Nested
    @DisplayName("spoolChange")
    class SpoolChange {

        @Test
        @DisplayName("calls client.spoolChange and returns ok")
        void changesSpool() {
            var request = new BambuMqttClient.SpoolChangeRequest();
            request.setAmsId(0);
            request.setTrayId(1);
            request.setTrayInfoIdx("GFL99");
            request.setTrayColor("FF0000FF");
            request.setTrayType("PLA");

            ResponseEntity<?> response = controller.skipObjects(request);

            verify(client).spoolChange(request);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("pausePrint")
    class PausePrint {

        @Test
        @DisplayName("calls client.pause and returns ok")
        void pausesPrint() {
            ResponseEntity<?> response = controller.pausePrint();

            verify(client).pause();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("status", "ok");
        }
    }

    @Nested
    @DisplayName("resumePrint")
    class ResumePrint {

        @Test
        @DisplayName("calls client.resume and returns ok")
        void resumesPrint() {
            ResponseEntity<?> response = controller.resumePrint();

            verify(client).resume();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("status", "ok");
        }
    }

    @Nested
    @DisplayName("stopPrint")
    class StopPrint {

        @Test
        @DisplayName("calls client.stop and returns ok")
        void stopsPrint() {
            ResponseEntity<?> response = controller.stopPrint();

            verify(client).stop();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("status", "ok");
        }
    }

    @Nested
    @DisplayName("pushAll")
    class PushAll {

        @Test
        @DisplayName("calls client.pushAll and returns ok")
        void pushesAll() {
            ResponseEntity<?> response = controller.pushAll();

            verify(client).pushAll();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("status", "ok");
        }
    }

    @Nested
    @DisplayName("fetchMetadata")
    class FetchMetadata {

        @Test
        @DisplayName("calls modelService.fetchMetadata and returns ok")
        void fetchesMetadata() throws Exception {
            ResponseEntity<?> response = controller.fetchMetadata();

            verify(modelService).fetchMetadata();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("status", "ok");
        }
    }
}