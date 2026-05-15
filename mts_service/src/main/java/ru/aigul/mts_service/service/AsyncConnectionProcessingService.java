package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncConnectionProcessingService {

    private final ApplicationConnectionWorkflowService connectionWorkflowService;

    public void process(ConnectionRequestedMessage message) {
        log.info("Received ConnectionRequested message: applicationId={}, correlationId={}",
                message.applicationId(), message.correlationId());
        connectionWorkflowService.connectAsynchronously(message.applicationId(), message.correlationId());
    }
}

