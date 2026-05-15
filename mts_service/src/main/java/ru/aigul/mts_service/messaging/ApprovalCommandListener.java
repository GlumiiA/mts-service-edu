package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.service.AsyncApprovalProcessingService;
import ru.aigul.mts_service.service.ListenerFailureHandler;
import ru.aigul.mts_service.service.MessageIdempotencyService;
import ru.aigul.mts_service.service.OutboxService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalCommandListener {

    private final AsyncApprovalProcessingService asyncApprovalProcessingService;
    private final MessageIdempotencyService messageIdempotencyService;
    private final ListenerFailureHandler listenerFailureHandler;

    @JmsListener(destination = "${app.messaging.approval.queue-address}", containerFactory = "jmsListenerContainerFactory")
    public void onApprovalRequested(ApprovalRequestedMessage message,
                                    @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
        String messageId = message.messageId();
        if (!messageIdempotencyService.tryRegister(messageId, "approval-listener")) {
            log.info("Duplicate approval message skipped: messageId={}, applicationId={}",
                    messageId, message.applicationId());
            return;
        }

        try {
            log.info("JMS listener consumed message: messageId={}, applicationId={}, correlationId={}",
                    messageId, message.applicationId(), message.correlationId());
            asyncApprovalProcessingService.process(message);
        } catch (Exception ex) {
            boolean shouldRetry = listenerFailureHandler.shouldRetryOrMoveToDlq(
                    OutboxService.EVENT_APPROVAL_REQUESTED,
                    messageId,
                    message,
                    deliveryCount,
                    ex
            );
            if (shouldRetry) {
                throw ex;
            }
        }
    }
}
