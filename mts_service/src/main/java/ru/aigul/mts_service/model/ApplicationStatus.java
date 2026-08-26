package ru.aigul.mts_service.model;

public enum ApplicationStatus {
    PENDING_TAIGA_SYNC,
    PENDING,
    PROCESSING,
    APPROVED,
    REJECTED,
    CONNECTED,
    FAILED_EXTERNAL
}
