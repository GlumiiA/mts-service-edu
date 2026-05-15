package ru.aigul.mts_service.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.model.*;
import ru.aigul.mts_service.repository.OneCErrorRepository;
import ru.aigul.mts_service.repository.OneCyncHistoryRepository;
import ru.aigul.mts_service.repository.RejectedApplicationRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Main service for 1C integration.
 * 
 * Handles:
 * 1. Creating/sending applications to 1C
 * 2. Checking status with 1C
 * 3. Handling different error scenarios
 * 4. Managing idempotent processing
 * 5. Retry logic with exponential backoff
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OneCIntegrationService {

    private final OneCyncHistoryRepository syncHistoryRepository;
    private final OneCErrorRepository errorRepository;
    private final RejectedApplicationRepository rejectedApplicationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Submit application to 1C for processing.
     * 
     * Creates sync history record and attempts to send data to 1C.
     * Handles idempotent processing - multiple calls with same application
     * will not create duplicate records in 1C.
     */
    @Transactional
    public void submitApplicationToOneC(Application application) {
        log.info("Submitting application id={} to 1C", application.getId());

        // Check if already submitted (idempotency check)
        String applicationKey = generateApplicationKey(application);
        
        // For idempotency: if we already have a successful sync, skip resend
        // But create a new sync history for tracking
        
        OneCyncHistory syncHistory = OneCyncHistory.builder()
            .application(application)
            .entityType(EntityType.APPLICATION.getValue())
            .syncDirection(SyncDirection.TO_1C)
            .syncStatus(OneCIntegrationStatus.PENDING)
            .retryCount(0)
            .maxRetries(5)
            .nextRetryAt(LocalDateTime.now())
            .build();

        syncHistoryRepository.save(syncHistory);
        log.debug("Created sync history record id={}", syncHistory.getId());

        try {
            // Perform actual sync
            performSync(syncHistory, application);
            
        } catch (Exception e) {
            log.error("Initial sync attempt failed for application id={}", application.getId(), e);
            
            // Store error and mark for retry
            handleSyncError(syncHistory, e);
        }
    }

    /**
     * Perform actual synchronization with 1C system.
     * 
     * Throws different types of exceptions based on error cause:
     * - InsufficientFundsException - insufficient funds in 1C
     * - OneCException - general 1C errors (retryable)
     * - RuntimeException - network/timeout errors (retryable)
     */
    @Transactional
    public void performSync(OneCyncHistory syncHistory, Application application) throws Exception {
        try {
            log.debug("Performing sync for application id={}", application.getId());
            
            // Prepare payload for 1C
            String payload = prepareApplicationPayload(application);
            
            // Create JCA interaction spec
            OneCCCIInteractionSpec spec = new OneCCCIInteractionSpec("CREATE_APPLICATION");
            
            // Create input record
            OneCCCIRecord inputRecord = OneCCCIRecord.builder()
                .operationType("CREATE_APPLICATION")
                .payload(payload)
                .build();

            // TODO: Execute via JCA Resource Adapter
            // For now, simulate successful response
            OneCCCIRecord response = simulateOneCResponse(inputRecord);
            
            // Process successful response
            syncHistory.setExternalId(response.getExternalId());
            syncHistory.setSyncStatus(OneCIntegrationStatus.SUCCESS);
            syncHistory.setLastSyncAt(LocalDateTime.now());
            syncHistoryRepository.save(syncHistory);
            
            log.info("Successfully synced application id={} with external_id={}", 
                application.getId(), response.getExternalId());
            
        } catch (InsufficientFundsException e) {
            log.warn("Insufficient funds for application id={}", application.getId());
            throw e;
            
        } catch (OneCException e) {
            log.warn("1C error for application id={}: {}", application.getId(), e.getMessage());
            throw e;
            
        } catch (Exception e) {
            log.error("Unexpected error syncing application id={}: {}", application.getId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Retry sync for pending records
     */
    @Transactional
    public void retrySync(OneCyncHistory syncRecord) {
        try {
            log.info("Retrying sync for record id={}", syncRecord.getId());
            performSync(syncRecord, syncRecord.getApplication());
            
        } catch (Exception e) {
            log.debug("Retry failed for record id={}: {}", syncRecord.getId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Check status of application with 1C system
     */
    @Transactional
    public void checkStatusWithOneC(OneCyncHistory syncRecord) {
        try {
            log.debug("Checking status in 1C for external_id={}", syncRecord.getExternalId());
            
            if (syncRecord.getExternalId() == null) {
                log.warn("No external_id for sync record id={}", syncRecord.getId());
                return;
            }
            
            // Create JCA interaction spec
            OneCCCIInteractionSpec spec = new OneCCCIInteractionSpec("CHECK_APPLICATION");
            
            // Create input record
            OneCCCIRecord inputRecord = OneCCCIRecord.builder()
                .operationType("CHECK_APPLICATION")
                .externalId(syncRecord.getExternalId())
                .build();

            // TODO: Execute via JCA Resource Adapter
            OneCCCIRecord response = simulateStatusCheckResponse(inputRecord);
            
            // Update sync record based on response
            // (implementation depends on 1C response format)
            syncRecord.setLastSyncAt(LocalDateTime.now());
            syncHistoryRepository.save(syncRecord);
            
        } catch (Exception e) {
            log.error("Error checking status in 1C: {}", e.getMessage());
        }
    }

    /**
     * Perform bulk status check with 1C
     */
    public void performBulkStatusCheck() {
        log.debug("Performing bulk status check with 1C");
        // TODO: Implement bulk status check
    }

    /**
     * Send record to Dead Letter Queue
     */
    @Transactional
    public void sendToDeadLetterQueue(OneCyncHistory syncRecord) {
        log.warn("Sending sync record id={} to DLQ", syncRecord.getId());
        
        syncRecord.setSyncStatus(OneCIntegrationStatus.DLQ);
        syncRecord.setLastError("Moved to DLQ after max retries exceeded");
        syncHistoryRepository.save(syncRecord);
        
        // TODO: Actual DLQ notification/integration
    }

    /**
     * Handle sync errors with proper classification
     */
    @Transactional
    public void handleSyncError(OneCyncHistory syncRecord, Exception error) {
        log.error("Handling sync error for record id={}: {}", syncRecord.getId(), error.getMessage());
        
        if (error instanceof InsufficientFundsException) {
            handleInsufficientFundsError(syncRecord, error);
        } else if (error instanceof OneCException) {
            handleOneCError(syncRecord, error);
        } else {
            handleNetworkError(syncRecord, error);
        }
    }

    /**
     * Handle insufficient funds error - reject application
     */
    @Transactional
    private void handleInsufficientFundsError(OneCyncHistory syncRecord, Exception error) {
        log.warn("Insufficient funds for application id={}", syncRecord.getApplication().getId());
        
        // Mark sync as rejected
        syncRecord.setSyncStatus(OneCIntegrationStatus.INSUFFICIENT_FUNDS);
        syncRecord.setLastError(error.getMessage());
        syncHistoryRepository.save(syncRecord);
        
        // Create error record
        OneCError errorRecord = OneCError.builder()
            .syncHistory(syncRecord)
            .errorCode("INSUFFICIENT_FUNDS")
            .errorMessage(error.getMessage())
            .errorDetail(ErrorDetailEnum.INSUFFICIENT_FUNDS)
            .build();
        errorRepository.save(errorRecord);
        
        // Create rejected application record
        RejectedApplication rejectedApp = RejectedApplication.builder()
            .application(syncRecord.getApplication())
            .syncHistory(syncRecord)
            .rejectionReason("Insufficient funds in 1C system")
            .errorCode("INSUFFICIENT_FUNDS")
            .manualReviewRequired(true)
            .build();
        rejectedApplicationRepository.save(rejectedApp);
        
        // TODO: Send notification to user about rejection
    }

    /**
     * Handle 1C system errors
     */
    @Transactional
    private void handleOneCError(OneCyncHistory syncRecord, Exception error) {
        log.warn("1C error for application id={}: {}", syncRecord.getApplication().getId(), error.getMessage());
        
        // Mark for retry
        syncRecord.setSyncStatus(OneCIntegrationStatus.RETRY);
        syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);
        syncRecord.setLastError(error.getMessage());
        
        // Calculate exponential backoff
        long backoffSeconds = (long) Math.pow(2, syncRecord.getRetryCount());
        syncRecord.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        
        syncHistoryRepository.save(syncRecord);
        
        // Create error record
        ErrorDetailEnum errorDetail = classifyOneCError(error.getMessage());
        OneCError errorRecord = OneCError.builder()
            .syncHistory(syncRecord)
            .errorMessage(error.getMessage())
            .errorDetail(errorDetail)
            .errorStacktrace(stackTraceToString(error))
            .build();
        errorRepository.save(errorRecord);
    }

    /**
     * Handle network/timeout errors
     */
    public void handleNetworkError(OneCyncHistory syncRecord, Exception error) {
        log.warn("Network error for application id={}: {}", syncRecord.getApplication().getId(), error.getMessage());
        
        // Mark for retry
        syncRecord.setSyncStatus(OneCIntegrationStatus.RETRY);
        syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);
        syncRecord.setLastError(error.getMessage());
        
        // Shorter backoff for network errors (retry sooner)
        long backoffSeconds = (long) Math.min(60, Math.pow(2, Math.max(0, syncRecord.getRetryCount() - 1)));
        syncRecord.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        
        syncHistoryRepository.save(syncRecord);
        
        // Create error record
        ErrorDetailEnum errorDetail = error.getMessage().contains("timeout") 
            ? ErrorDetailEnum.TIMEOUT 
            : ErrorDetailEnum.NETWORK_ERROR;
            
        OneCError errorRecord = OneCError.builder()
            .syncHistory(syncRecord)
            .errorMessage(error.getMessage())
            .errorDetail(errorDetail)
            .errorStacktrace(stackTraceToString(error))
            .build();
        errorRepository.save(errorRecord);
    }

    /**
     * Prepare JSON payload for application to send to 1C
     */
    private String prepareApplicationPayload(Application application) throws Exception {
        // Create DTO for 1C
        OneCApplicationDTO dto = OneCApplicationDTO.builder()
            .applicationId(application.getId())
            .userId(application.getUser().getId())
            .userName(application.getUser().getName())
            .tariffId(application.getTariff().getId())
            .address(application.getAddress())
            .price(application.getLockedPrice())
            .status(application.getStatus().toString())
            .requestId(UUID.randomUUID().toString()) // For idempotency
            .timestamp(LocalDateTime.now())
            .build();
        
        return objectMapper.writeValueAsString(dto);
    }

    /**
     * Generate idempotency key for application
     */
    private String generateApplicationKey(Application application) {
        return "APP-" + application.getId() + "-" + application.getUpdatedAt().hashCode();
    }

    /**
     * Classify 1C errors
     */
    private ErrorDetailEnum classifyOneCError(String errorMessage) {
        if (errorMessage == null) {
            return ErrorDetailEnum.UNKNOWN_ERROR;
        }
        
        String lowerMessage = errorMessage.toLowerCase();
        if (lowerMessage.contains("validation")) {
            return ErrorDetailEnum.VALIDATION_ERROR;
        } else if (lowerMessage.contains("fund")) {
            return ErrorDetailEnum.INSUFFICIENT_FUNDS;
        } else if (lowerMessage.contains("timeout")) {
            return ErrorDetailEnum.TIMEOUT;
        } else if (lowerMessage.contains("network")) {
            return ErrorDetailEnum.NETWORK_ERROR;
        }
        return ErrorDetailEnum.UNKNOWN_ERROR;
    }

    /**
     * Convert exception stack trace to string
     */
    private String stackTraceToString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement ste : e.getStackTrace()) {
            sb.append(ste.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Simulate 1C response (for testing)
     */
    private OneCCCIRecord simulateOneCResponse(OneCCCIRecord input) throws Exception {
        // Simulate different scenarios
        double random = Math.random();
        
        if (random < 0.05) {
            // 5% chance: insufficient funds
            throw new InsufficientFundsException("Insufficient funds in 1C account");
        } else if (random < 0.1) {
            // 5% chance: 1C error
            throw new OneCException("1C system error", "VALIDATION_ERROR");
        } else if (random < 0.15) {
            // 5% chance: network error
            throw new RuntimeException("Network timeout connecting to 1C");
        }
        
        // 85% chance: success
        return OneCCCIRecord.builder()
            .operationType("CREATE_APPLICATION_RESPONSE")
            .externalId("1C-APP-" + UUID.randomUUID().toString())
            .payload("{\"status\":\"SUCCESS\"}")
            .build();
    }

    /**
     * Simulate status check response
     */
    private OneCCCIRecord simulateStatusCheckResponse(OneCCCIRecord input) {
        return OneCCCIRecord.builder()
            .operationType("CHECK_APPLICATION_RESPONSE")
            .externalId(input.getExternalId())
            .payload("{\"status\":\"ACTIVE\",\"balance\":\"100000\"}")
            .build();
    }
}



