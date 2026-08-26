package ru.aigul.mts_service.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("User not found: " + id);
    }

    public UserNotFoundException(String email) {
        super("User not found: " + email);
    }
}
