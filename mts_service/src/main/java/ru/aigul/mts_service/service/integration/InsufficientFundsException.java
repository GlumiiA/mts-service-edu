package ru.aigul.mts_service.service.integration;

/**
 * Exception indicating insufficient funds in 1C system
 */
public class InsufficientFundsException extends Exception {
    
    public InsufficientFundsException(String message) {
        super(message);
    }
    
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
