package ru.aigul.mts_service.integration1С;


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
