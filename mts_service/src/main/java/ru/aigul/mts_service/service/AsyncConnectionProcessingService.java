package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncConnectionProcessingService {

    private final ApplicationConnectionWorkflowService connectionWorkflowService;
    private final MessageInboxService messageInboxService;

    @Transactional
    public void process(ConnectionRequestedMessage message) {
        if (!messageInboxService.register(message.messageId(), "connection-listener")) {
            log.info("Duplicate connection message skipped: messageId={}, applicationId={}",
                    message.messageId(), message.applicationId());
            return;
        }

        log.info("Received ConnectionRequested message: applicationId={}, correlationId={}",
                message.applicationId(), message.correlationId());
        connectionWorkflowService.connectAsynchronously(message.applicationId(), message.correlationId());
    }
}

