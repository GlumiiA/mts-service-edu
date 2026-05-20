package ru.aigul.mts_service.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;
import ru.aigul.mts_service.model.OutboxMessage;
import ru.aigul.mts_service.model.OutboxMessageStatus;
import ru.aigul.mts_service.repository.OutboxMessageRepository;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.approval.queue-address}")
    private String approvalQueueAddress;

    @Value("${app.messaging.connection.queue-address}")
    private String connectionQueueAddress;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueApprovalRequested(ApprovalRequestedMessage message) {
        persist(approvalQueueAddress, "approval-requested", message.messageId(), message);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueConnectionRequested(ConnectionRequestedMessage message) {
        persist(connectionQueueAddress, "connection-requested", message.messageId(), message);
    }

    private void persist(String destination, String eventType, String messageId, Object payload) {
        try {
            OutboxMessage outboxMessage = new OutboxMessage();
            outboxMessage.setMessageId(messageId);
            outboxMessage.setDestination(destination);
            outboxMessage.setEventType(eventType);
            outboxMessage.setPayloadType(payload.getClass().getName());
            outboxMessage.setPayloadJson(objectMapper.writeValueAsString(payload));
            outboxMessage.setStatus(OutboxMessageStatus.NEW);
            outboxMessage.setAttempts(0);
            outboxMessage.setNextAttemptAt(OffsetDateTime.now());
            outboxMessageRepository.save(outboxMessage);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
