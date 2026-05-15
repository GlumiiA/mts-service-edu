package ru.aigul.mts_service.messaging.dto;

import java.time.OffsetDateTime;

public record ConnectionRequestedMessage(
        String messageId,
        Long applicationId,
        String correlationId,
        OffsetDateTime requestedAt
) {
}

