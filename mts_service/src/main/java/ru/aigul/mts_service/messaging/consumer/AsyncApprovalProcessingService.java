package ru.aigul.mts_service.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.messaging.inbox.MessageInboxService;
import ru.aigul.mts_service.service.ApplicationApprovalWorkflowService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncApprovalProcessingService {

    private final ApplicationApprovalWorkflowService workflowService;
    private final MessageInboxService messageInboxService;

    @Transactional
    public void process(ApprovalRequestedMessage message) {
        if (!messageInboxService.register(message.messageId(), "approval-listener")) {
            log.info("Duplicate approval message skipped: messageId={}, applicationId={}",
                    message.messageId(), message.applicationId());
            return;
        }

        log.info("Received ApprovalRequested message: messageId={}, applicationId={}, correlationId={}, requestedBy={}",
                message.messageId(), message.applicationId(), message.correlationId(), message.requestedBy());
        workflowService.approveAsynchronously(
                message.applicationId(),
                message.requestedBy(),
                message.correlationId()
        );
    }
}
