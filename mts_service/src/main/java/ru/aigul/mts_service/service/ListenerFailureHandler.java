package ru.aigul.mts_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.messaging.DeadLetterPublisher;
import ru.aigul.mts_service.messaging.dto.DeadLetterMessage;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListenerFailureHandler {

    private final DeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;

    @Value("${app.messaging.listener.max-delivery-attempts:5}")
    private int maxDeliveryAttempts;

    public boolean shouldRetryOrMoveToDlq(String eventType,
                                          String messageId,
                                          Object payload,
                                          Integer deliveryCount,
                                          Exception ex) {
        int attempts = deliveryCount == null ? 1 : deliveryCount;
        if (attempts < maxDeliveryAttempts) {
            return true;
        }

        deadLetterPublisher.publish(new DeadLetterMessage(
                messageId,
                eventType,
                toJson(payload),
                attempts,
                ex.getMessage(),
                OffsetDateTime.now()
        ));

        log.error("Message sent to DLQ after max delivery attempts: messageId={}, eventType={}, attempts={}",
                messageId, eventType, attempts, ex);
        return false;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return String.valueOf(payload);
        }
    }
}

