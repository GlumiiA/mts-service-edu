package ru.aigul.mts_service.messaging.dto;

import java.time.OffsetDateTime;

public record DeadLetterMessage(
        String messageId,
        String eventType,
        String payload,
        int attempts,
        String error,
        OffsetDateTime failedAt
) {
}

