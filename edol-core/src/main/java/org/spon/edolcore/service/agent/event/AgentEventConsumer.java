package org.spon.edolcore.service.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.model.dto.AgentEventDto;
import org.spon.edolcore.service.model.transfer.ModelTransferWorkflowService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentEventConsumer {

    private final ModelTransferWorkflowService modelTransferWorkflowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void consume(UUID printerId, String payload) {
        try {
            AgentEventDto event =
                    objectMapper.readValue(
                            payload,
                            AgentEventDto.class
                    );

            switch (event.getType()) {
                case MODEL_UPLOAD_STARTED -> modelTransferWorkflowService.onUploadStarted(
                        printerId,
                        event.getFileName()
                );

                case MODEL_UPLOAD_COMPLETED -> modelTransferWorkflowService.onUploadCompleted(
                        printerId,
                        event.getFileName()
                );

                case MODEL_UPLOAD_FAILED -> modelTransferWorkflowService.onUploadFailed(
                        printerId,
                        event.getFileName(),
                        event.getReason()
                );
            }

        } catch (Exception e) {
            log.error(
                    "Failed to process agent event: {}",
                    payload,
                    e
            );
        }
    }
}