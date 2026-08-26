package ru.aigul.mts_service.dto.application;

public record AsyncApprovalAcceptedDto(
        Long applicationId,
        String correlationId,
        String status
) {
}
