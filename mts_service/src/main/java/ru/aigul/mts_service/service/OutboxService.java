package ru.aigul.mts_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;
import ru.aigul.mts_service.model.OutboxMessage;
import ru.aigul.mts_service.model.OutboxMessageStatus;
import ru.aigul.mts_service.repository.OutboxMessageRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    public static final String EVENT_APPROVAL_REQUESTED = "APPLICATION_APPROVAL_REQUESTED";
    public static final String EVENT_CONNECTION_REQUESTED = "APPLICATION_CONNECTION_REQUESTED";

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.approval.queue-address}")
    private String approvalQueueAddress;

    @Value("${app.messaging.connection.queue-address}")
    private String connectionQueueAddress;

    @Transactional
    public String enqueueApprovalRequested(Long applicationId, String requestedBy, String correlationId) {
        ApprovalRequestedMessage message = new ApprovalRequestedMessage(
                UUID.randomUUID().toString(),
                applicationId,
                requestedBy,
                correlationId,
                OffsetDateTime.now()
        );
        save(serialize(message), EVENT_APPROVAL_REQUESTED, approvalQueueAddress, message.messageId());
        return message.messageId();
    }

    @Transactional
    public String enqueueConnectionRequested(Long applicationId, String correlationId) {
        ConnectionRequestedMessage message = new ConnectionRequestedMessage(
                UUID.randomUUID().toString(),
                applicationId,
                correlationId,
                OffsetDateTime.now()
        );
        save(serialize(message), EVENT_CONNECTION_REQUESTED, connectionQueueAddress, message.messageId());
        return message.messageId();
    }

    private void save(String payload, String eventType, String destination, String messageId) {
        OutboxMessage outboxMessage = new OutboxMessage();
        outboxMessage.setId(messageId);
        outboxMessage.setEventType(eventType);
        outboxMessage.setDestination(destination);
        outboxMessage.setPayload(payload);
        outboxMessage.setStatus(OutboxMessageStatus.PENDING);
        outboxMessage.setAttempts(0);
        outboxMessage.setNextAttemptAt(OffsetDateTime.now());
        outboxMessageRepository.save(outboxMessage);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}

