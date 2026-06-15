package ru.aigul.mts_service.integration.taiga;

public record TaigaStatusChangeEvent(
        long userStoryId,
        long statusId,
        String statusName,
        String changedBy
) {
}
