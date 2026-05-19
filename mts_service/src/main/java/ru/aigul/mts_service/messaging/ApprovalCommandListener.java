package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.service.AsyncApprovalProcessingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalCommandListener {

    private final AsyncApprovalProcessingService asyncApprovalProcessingService;

    @JmsListener(destination = "${app.messaging.approval.queue-address}", containerFactory = "jmsListenerContainerFactory")
    public void onApprovalRequested(ApprovalRequestedMessage message,
                                    @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
        try {
            log.info("JMS listener consumed message: messageId={}, applicationId={}, correlationId={}",
                    message.messageId(), message.applicationId(), message.correlationId());
            asyncApprovalProcessingService.process(message);
        } catch (Exception ex) {
            log.warn("Approval message processing failed: messageId={}, applicationId={}, deliveryCount={}",
                    message.messageId(), message.applicationId(), deliveryCount, ex);
            throw ex;
        }
    }
}
