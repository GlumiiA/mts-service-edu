package ru.aigul.mts_service.service.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for response from 1C system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCResponseDTO {
    
    private String status; // SUCCESS, FAILURE, etc.
    private String externalId; // 1C identifier
    private String errorCode;
    private String errorMessage;
    private String balance; // Available funds in 1C
    private String timestamp;
}
