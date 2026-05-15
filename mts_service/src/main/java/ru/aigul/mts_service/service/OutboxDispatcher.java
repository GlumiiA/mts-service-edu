package ru.aigul.mts_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.messaging.DeadLetterPublisher;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;
import ru.aigul.mts_service.messaging.dto.DeadLetterMessage;
import ru.aigul.mts_service.model.OutboxMessage;
import ru.aigul.mts_service.model.OutboxMessageStatus;
import ru.aigul.mts_service.repository.OutboxMessageRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;
    private final JmsTemplate jmsTemplate;
    private final DeadLetterPublisher deadLetterPublisher;

    @Value("${app.messaging.outbox.batch-size:20}")
    private int batchSize;

    @Value("${app.messaging.outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.messaging.outbox.retry-delay-ms:2000}")
    private long retryDelayMs;

    @Scheduled(fixedDelayString = "${app.messaging.outbox.dispatch-fixed-delay-ms:1000}")
    public void dispatchPendingMessages() {
        List<OutboxMessage> batch = outboxMessageRepository.findReadyToDispatch(
                Set.of(OutboxMessageStatus.PENDING),
                OffsetDateTime.now(),
                PageRequest.of(0, batchSize)
        );
        batch.forEach(this::dispatchOne);
    }

    @Transactional
    protected void dispatchOne(OutboxMessage outboxMessage) {
        try {
            Object payload = deserializePayload(outboxMessage);
            jmsTemplate.convertAndSend(outboxMessage.getDestination(), payload);
            outboxMessage.setStatus(OutboxMessageStatus.SENT);
            outboxMessage.setSentAt(OffsetDateTime.now());
            outboxMessage.setLastError(null);
            outboxMessageRepository.save(outboxMessage);
            log.info("Outbox message dispatched: id={}, eventType={}, destination={}",
                    outboxMessage.getId(), outboxMessage.getEventType(), outboxMessage.getDestination());
        } catch (Exception ex) {
            int attempts = outboxMessage.getAttempts() + 1;
            outboxMessage.setAttempts(attempts);
            outboxMessage.setLastError(ex.getMessage());

            if (attempts >= maxAttempts) {
                outboxMessage.setStatus(OutboxMessageStatus.DEAD);
                deadLetterPublisher.publish(new DeadLetterMessage(
                        outboxMessage.getId(),
                        outboxMessage.getEventType(),
                        outboxMessage.getPayload(),
                        attempts,
                        ex.getMessage(),
                        OffsetDateTime.now()
                ));
                log.error("Outbox message moved to DEAD state: id={}, eventType={}",
                        outboxMessage.getId(), outboxMessage.getEventType(), ex);
            } else {
                outboxMessage.setNextAttemptAt(OffsetDateTime.now().plusNanos(retryDelayMs * 1_000_000));
                log.warn("Outbox dispatch failed, will retry: id={}, attempts={}", outboxMessage.getId(), attempts, ex);
            }

            outboxMessageRepository.save(outboxMessage);
        }
    }

    private Object deserializePayload(OutboxMessage outboxMessage) throws Exception {
        return switch (outboxMessage.getEventType()) {
            case OutboxService.EVENT_APPROVAL_REQUESTED ->
                    objectMapper.readValue(outboxMessage.getPayload(), ApprovalRequestedMessage.class);
            case OutboxService.EVENT_CONNECTION_REQUESTED ->
                    objectMapper.readValue(outboxMessage.getPayload(), ConnectionRequestedMessage.class);
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + outboxMessage.getEventType());
        };
    }
}

