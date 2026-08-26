package ru.aigul.mts_service.exception;

public class TaigaIntegrationException extends RuntimeException {

    public TaigaIntegrationException(String message) {
        super(message);
    }

    public TaigaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
