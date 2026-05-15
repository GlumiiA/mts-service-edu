package ru.aigul.mts_service.service.integration;

/**
 * Exception for 1C system errors
 */
public class OneCException extends Exception {
    
    private String errorCode;
    
    public OneCException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public OneCException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}
