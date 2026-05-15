package ru.aigul.mts_service.service.integration;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * InteractionSpec implementation for 1C integration operations.
 * 
 * Specifies what kind of operation to perform with 1C system.
 */
@Data
@AllArgsConstructor
public class OneCCCIInteractionSpec {

    private String operationType; // CREATE_APPLICATION, CHECK_APPLICATION, etc.
    private String timeout; // Optional timeout in milliseconds
    private boolean functionNameBasedExecution = true;

    public OneCCCIInteractionSpec(String operationType) {
        this.operationType = operationType;
        this.timeout = "30000"; // Default 30 seconds
    }
}


