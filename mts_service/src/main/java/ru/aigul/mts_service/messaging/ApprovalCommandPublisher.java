package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalCommandPublisher {

    private final JmsTemplate jmsTemplate;

    @Value("${app.messaging.approval.queue-address}")
    private String approvalQueueAddress;

    public void publish(ApprovalRequestedMessage message) {
        jmsTemplate.convertAndSend(approvalQueueAddress, message);
        log.info("Published ApprovalRequested message: applicationId={}, correlationId={}, destination={}",
                message.applicationId(), message.correlationId(), approvalQueueAddress);
    }
}
