package ru.aigul.mts_service.messaging.dto;

import java.time.OffsetDateTime;

public record TaigaStoryRequestedMessage(
        String messageId,
        Long applicationId,
        String correlationId,
        OffsetDateTime requestedAt
) {
}
