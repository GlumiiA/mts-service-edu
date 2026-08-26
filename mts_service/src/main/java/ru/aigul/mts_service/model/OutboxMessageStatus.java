package ru.aigul.mts_service.model;

public enum OutboxMessageStatus {
    NEW,
    PROCESSING,
    SENT,
    FAILED
}
