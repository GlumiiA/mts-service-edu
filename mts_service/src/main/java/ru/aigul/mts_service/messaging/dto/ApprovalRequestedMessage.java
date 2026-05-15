package ru.aigul.mts_service.messaging.dto;

import java.time.OffsetDateTime;

public record ApprovalRequestedMessage(
        String messageId,
        Long applicationId,
        String requestedBy,
        String correlationId,
        OffsetDateTime requestedAt
) {
}
