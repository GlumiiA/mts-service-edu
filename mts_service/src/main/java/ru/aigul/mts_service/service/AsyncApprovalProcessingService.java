package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncApprovalProcessingService {

    private final ApplicationApprovalWorkflowService workflowService;

    public void process(ApprovalRequestedMessage message) {
        log.info("Received ApprovalRequested message: messageId={}, applicationId={}, correlationId={}, requestedBy={}",
                message.messageId(), message.applicationId(), message.correlationId(), message.requestedBy());
        workflowService.approveAsynchronously(
                message.applicationId(),
                message.requestedBy(),
                message.correlationId()
        );
    }
}
