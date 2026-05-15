package ru.aigul.mts_service.service.integration;

import lombok.extern.slf4j.Slf4j;

/**
 * Interaction handler for 1C integration.
 * 
 * Handles the actual communication protocol with 1C system.
 */
@Slf4j
public class OneCCCIInteraction {

    private boolean closed = false;

    public OneCCCIRecord execute(OneCCCIInteractionSpec spec, OneCCCIRecord input) {
        if (closed) {
            throw new RuntimeException("Interaction is closed");
        }

        if (spec == null) {
            throw new RuntimeException("Invalid InteractionSpec");
        }

        log.debug("Executing 1C interaction: operation={}", spec.getOperationType());

        // Execute the interaction based on operation type
        OneCCCIRecord result = executeOperation(spec, input);
        
        return result;
    }

    public boolean execute(OneCCCIInteractionSpec spec, OneCCCIRecord input, OneCCCIRecord output) {
        if (closed) {
            throw new RuntimeException("Interaction is closed");
        }

        OneCCCIRecord result = execute(spec, input);
        
        // Copy result to output record
        output.setPayload(result.getPayload());
        output.setExternalId(result.getExternalId());
        
        return true;
    }

    /**
     * Internal method to execute 1C operation
     */
    private OneCCCIRecord executeOperation(OneCCCIInteractionSpec spec, OneCCCIRecord input) {
        try {
            return switch (spec.getOperationType()) {
                case "CREATE_APPLICATION" -> createApplicationInOneC(input);
                case "CHECK_APPLICATION" -> checkApplicationStatusInOneC(input);
                default -> throw new RuntimeException("Unknown operation: " + spec.getOperationType());
            };
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute operation: " + e.getMessage(), e);
        }
    }

    /**
     * Create application in 1C
     */
    private OneCCCIRecord createApplicationInOneC(OneCCCIRecord input) {
        log.info("Creating application in 1C: {}", input.getPayload());
        
        // TODO: Actual 1C API call would go here
        // For now, return mock response
        return OneCCCIRecord.builder()
            .operationType("CREATE_APPLICATION_RESPONSE")
            .externalId("1C-" + System.currentTimeMillis())
            .payload("{\"status\":\"SUCCESS\",\"id\":\"" + System.currentTimeMillis() + "\"}")
            .build();
    }

    /**
     * Check application status in 1C
     */
    private OneCCCIRecord checkApplicationStatusInOneC(OneCCCIRecord input) {
        log.info("Checking application status in 1C: {}", input.getPayload());
        
        // TODO: Actual 1C API call would go here
        return OneCCCIRecord.builder()
            .operationType("CHECK_APPLICATION_RESPONSE")
            .externalId(input.getExternalId())
            .payload("{\"status\":\"ACTIVE\"}")
            .build();
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}


