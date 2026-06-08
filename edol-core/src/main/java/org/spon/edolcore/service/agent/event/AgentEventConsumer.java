package org.spon.edolcore.service.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edolcore.model.dto.AgentEventDto;
import org.spon.edolcore.service.model.transfer.ModelTransferWorkflowService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentEventConsumer {

    private final ModelTransferWorkflowService modelTransferWorkflowService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void consume(String payload) {
        try {
            AgentEventDto event =
                    objectMapper.readValue(
                            payload,
                            AgentEventDto.class
                    );

            switch (event.getType()) {
                case MODEL_UPLOAD_STARTED -> modelTransferWorkflowService.onUploadStarted(
                        event.getFileName()
                );

                case MODEL_UPLOAD_COMPLETED -> modelTransferWorkflowService.onUploadCompleted(
                        event.getFileName()
                );

                case MODEL_UPLOAD_FAILED -> modelTransferWorkflowService.onUploadFailed(
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