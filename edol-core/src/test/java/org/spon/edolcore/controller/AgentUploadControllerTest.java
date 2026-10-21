package org.spon.edolcore.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.spon.edolcore.event.model.ModelAvailableEvent;
import org.spon.edolcore.persistence.printer.Printer;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.spon.edolcore.service.camera.CameraSnapshotStore;
import org.spon.edolcore.service.upload.ModelArchiveValidator;
import org.spon.edolcore.service.upload.ModelFileStorage;
import org.spon.edolcore.service.upload.SnapshotValidator;
import org.spon.edolcore.service.upload.UploadFixtures;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentUploadControllerTest {
    @TempDir
    Path temporary;
    private final UUID printerId = UUID.randomUUID();
    private final CameraSnapshotStore snapshots = mock(CameraSnapshotStore.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PrinterConnectionConfigurationRepository connections = mock(PrinterConnectionConfigurationRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var limits = UploadFixtures.limits();
        var storage = new ModelFileStorage(limits, new ModelArchiveValidator(limits), temporary.resolve("models"));
        var controller = new AgentUploadController(snapshots, events, connections, storage, new SnapshotValidator(limits));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void acceptsExistingHomeUploadContractAndPublishesOnlyTheCompleteStoredModel() throws Exception {
        knownAgent();
        byte[] body = UploadFixtures.model(Map.of());
        doAnswer(invocation -> {
            ModelAvailableEvent available = invocation.getArgument(0);
            assertThat(Files.readAllBytes(available.modelFile())).isEqualTo(body);
            return null;
        }).when(events).publishEvent(any(ModelAvailableEvent.class));
        mvc.perform(post("/api/agent/upload").header("X-Agent-Id", "synthetic-agent")
                        .param("fileName", "part.3mf").contentType(MediaType.APPLICATION_OCTET_STREAM).content(body))
                .andExpect(status().isOk());
        var event = ArgumentCaptor.forClass(ModelAvailableEvent.class);
        verify(events).publishEvent(event.capture());
        Path stored = temporary.resolve("models").resolve(printerId.toString()).resolve("part.3mf");
        assertThat(Files.readAllBytes(stored)).isEqualTo(body);
        assertThat(event.getValue().printerId()).isEqualTo(printerId);
    }

    @Test
    void rejectedModelNeverPublishesOrOverwritesThePreviousFile() throws Exception {
        knownAgent();
        Path directory = Files.createDirectories(temporary.resolve("models").resolve(printerId.toString()));
        Path previous = Files.writeString(directory.resolve("part.3mf"), "previous-model");
        mvc.perform(post("/api/agent/upload").header("X-Agent-Id", "synthetic-agent")
                        .param("fileName", "part.3mf").contentType(MediaType.APPLICATION_OCTET_STREAM).content("not a ZIP"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/agent/upload").header("X-Agent-Id", "synthetic-agent")
                        .param("fileName", "../escape.3mf").contentType(MediaType.APPLICATION_OCTET_STREAM).content("bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/agent/upload").header("X-Agent-Id", "synthetic-agent")
                        .param("fileName", "part.3mf").contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[100_001]))
                .andExpect(status().isPayloadTooLarge());
        assertThat(Files.readString(previous)).isEqualTo("previous-model");
        verifyNoInteractions(events, snapshots);
    }

    @Test
    void rejectsInvalidOrOversizedSnapshotsBeforeChangingCameraState() throws Exception {
        knownAgent();
        mvc.perform(post("/api/agent/camera/snapshot").header("X-Agent-Id", "synthetic-agent")
                        .contentType(MediaType.IMAGE_JPEG).content("not a JPEG"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/agent/camera/snapshot").header("X-Agent-Id", "synthetic-agent")
                        .contentType(MediaType.IMAGE_JPEG).content(new byte[8193]))
                .andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(events, snapshots);
    }

    @Test
    void acceptsAnOrdinaryJpegWithoutAuthenticationPrerequisitesInTheHomeHandler() throws Exception {
        knownAgent();
        byte[] image = UploadFixtures.jpeg();
        mvc.perform(post("/api/agent/camera/snapshot").header("X-Agent-Id", "synthetic-agent")
                        .contentType(MediaType.IMAGE_JPEG).content(image))
                .andExpect(status().isOk());
        verify(snapshots).store(printerId, image);
    }

    @Test
    void rejectsUnknownAgentBeforeReadingTheBodyOrPublishing() throws Exception {
        when(connections.findByAgentId(anyString())).thenReturn(Optional.empty());
        mvc.perform(post("/api/agent/upload").header("X-Agent-Id", "unknown-synthetic-agent")
                        .param("fileName", "part.3mf").contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[100_001]))
                .andExpect(status().isNotFound());
        verifyNoInteractions(events, snapshots);
        assertThat(temporary.resolve("models")).doesNotExist();
    }

    private void knownAgent() {
        var printer = Printer.builder().id(printerId).build();
        when(connections.findByAgentId("synthetic-agent"))
                .thenReturn(Optional.of(PrinterConnectionConfiguration.builder().printer(printer).build()));
    }
}
