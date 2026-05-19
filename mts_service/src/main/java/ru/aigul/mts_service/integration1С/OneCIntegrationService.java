package ru.aigul.mts_service.integration1С;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.dto.OneCyncHistoryDTO;
import ru.aigul.mts_service.dto.RejectedApplicationDTO;
import ru.aigul.mts_service.dto.ReviewRejectionRequestDTO;
import ru.aigul.mts_service.model.*;
import ru.aigul.mts_service.repository.OneCErrorRepository;
import ru.aigul.mts_service.repository.OneCyncHistoryRepository;
import ru.aigul.mts_service.repository.RejectedApplicationRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OneCIntegrationService {

    private final OneCyncHistoryRepository syncHistoryRepository;
    private final OneCErrorRepository errorRepository;
    private final RejectedApplicationRepository rejectedApplicationRepository;
    private final OneCCCIInteraction oneCCCIInteraction;
    private final ObjectMapper objectMapper;

    @Transactional
    public void performSync(OneCyncHistory syncHistory, Application application) throws Exception {
        try {
            log.debug("Performing sync for application id={}", application.getId());

            String payload = prepareApplicationPayload(application);

            OneCCCIRecord inputRecord = OneCCCIRecord.builder()
                .operationType("CREATE_APPLICATION")
                .payload(payload)
                .build();

            OneCCCIRecord response = oneCCCIInteraction.execute(
                new OneCCCIInteractionSpec("CREATE_APPLICATION"),
                inputRecord
            );

            OneCResponseDTO responseDTO = objectMapper.readValue(response.getPayload(), OneCResponseDTO.class);
            validateCreateApplicationResponse(responseDTO);

            syncHistory.setExternalId(responseDTO.getExternalId() != null ? responseDTO.getExternalId() : response.getExternalId());
            syncHistory.setSyncStatus(OneCIntegrationStatus.SUCCESS);
            syncHistory.setLastSyncAt(LocalDateTime.now());
            syncHistoryRepository.save(syncHistory);

            log.info("Successfully synced application id={} with external_id={}",
                application.getId(), syncHistory.getExternalId());
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

    @Transactional
    public void checkStatusWithOneC(OneCyncHistory syncRecord) {
        try {
            log.debug("Checking status in 1C for external_id={}", syncRecord.getExternalId());

            if (syncRecord.getExternalId() == null) {
                log.warn("No external_id for sync record id={}", syncRecord.getId());
                return;
            }

            OneCCCIRecord inputRecord = OneCCCIRecord.builder()
                .operationType("CHECK_APPLICATION")
                .externalId(syncRecord.getExternalId())
                .build();

            OneCCCIRecord response = oneCCCIInteraction.execute(
                new OneCCCIInteractionSpec("CHECK_APPLICATION"),
                inputRecord
            );

            OneCResponseDTO responseDTO = objectMapper.readValue(response.getPayload(), OneCResponseDTO.class);
            if (responseDTO.getStatus() != null && !"SUCCESS".equalsIgnoreCase(responseDTO.getStatus())) {
                log.warn("1C status check returned non-success for sync record id={}, status={}, errorCode={}",
                    syncRecord.getId(), responseDTO.getStatus(), responseDTO.getErrorCode());
            }

            syncRecord.setLastSyncAt(LocalDateTime.now());
            syncHistoryRepository.save(syncRecord);
        } catch (Exception e) {
            log.error("Error checking status in 1C: {}", e.getMessage());
        }
    }

    public void performBulkStatusCheck() {
        log.debug("Performing bulk status check with 1C");
    }

    @Transactional
    public void sendToDeadLetterQueue(OneCyncHistory syncRecord) {
        log.warn("Sending sync record id={} to DLQ", syncRecord.getId());

        syncRecord.setSyncStatus(OneCIntegrationStatus.DLQ);
        syncRecord.setLastError("Moved to DLQ after max retries exceeded");
        syncHistoryRepository.save(syncRecord);
    }

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

    @Transactional
    private void handleInsufficientFundsError(OneCyncHistory syncRecord, Exception error) {
        log.warn("Insufficient funds for application id={}", syncRecord.getApplication().getId());

        syncRecord.setSyncStatus(OneCIntegrationStatus.INSUFFICIENT_FUNDS);
        syncRecord.setLastError(error.getMessage());
        syncHistoryRepository.save(syncRecord);

        OneCError errorRecord = OneCError.builder()
            .syncHistory(syncRecord)
            .errorCode("INSUFFICIENT_FUNDS")
            .errorMessage(error.getMessage())
            .errorDetail(ErrorDetailEnum.INSUFFICIENT_FUNDS)
            .build();
        errorRepository.save(errorRecord);

        RejectedApplication rejectedApp = RejectedApplication.builder()
            .application(syncRecord.getApplication())
            .syncHistory(syncRecord)
            .rejectionReason("Insufficient funds in 1C system")
            .errorCode("INSUFFICIENT_FUNDS")
            .manualReviewRequired(true)
            .build();
        rejectedApplicationRepository.save(rejectedApp);
    }

    @Transactional
    private void handleOneCError(OneCyncHistory syncRecord, Exception error) {
        log.warn("1C error for application id={}: {}", syncRecord.getApplication().getId(), error.getMessage());

        syncRecord.setSyncStatus(OneCIntegrationStatus.RETRY);
        syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);
        syncRecord.setLastError(error.getMessage());

        long backoffSeconds = (long) Math.pow(2, syncRecord.getRetryCount());
        syncRecord.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        syncHistoryRepository.save(syncRecord);

        ErrorDetailEnum errorDetail = classifyOneCError(error.getMessage());
        OneCError errorRecord = OneCError.builder()
            .syncHistory(syncRecord)
            .errorMessage(error.getMessage())
            .errorDetail(errorDetail)
            .errorStacktrace(stackTraceToString(error))
            .build();
        errorRepository.save(errorRecord);
    }

    public void handleNetworkError(OneCyncHistory syncRecord, Exception error) {
        log.warn("Network error for application id={}: {}", syncRecord.getApplication().getId(), error.getMessage());

        syncRecord.setSyncStatus(OneCIntegrationStatus.RETRY);
        syncRecord.setRetryCount(syncRecord.getRetryCount() + 1);
        syncRecord.setLastError(error.getMessage());

        long backoffSeconds = (long) Math.min(60, Math.pow(2, Math.max(0, syncRecord.getRetryCount() - 1)));
        syncRecord.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        syncHistoryRepository.save(syncRecord);

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

    private String prepareApplicationPayload(Application application) throws Exception {
        OneCApplicationDTO dto = OneCApplicationDTO.builder()
            .applicationId(application.getId())
            .userId(application.getUser().getId())
            .userName(application.getUser().getName())
            .tariffId(application.getTariff().getId())
            .address(application.getAddress())
            .price(application.getLockedPrice())
            .status(application.getStatus().toString())
            .requestId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .build();

        return objectMapper.writeValueAsString(dto);
    }

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

    private String stackTraceToString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement ste : e.getStackTrace()) {
            sb.append(ste.toString()).append("\n");
        }
        return sb.toString();
    }

    private void validateCreateApplicationResponse(OneCResponseDTO responseDTO) throws Exception {
        if (responseDTO == null) {
            throw new OneCException("Empty response from 1C", "EMPTY_RESPONSE");
        }

        String status = responseDTO.getStatus() == null ? "" : responseDTO.getStatus().trim().toUpperCase();
        String errorCode = responseDTO.getErrorCode();
        String errorMessage = responseDTO.getErrorMessage() != null
            ? responseDTO.getErrorMessage()
            : "1C returned an unsuccessful response";

        if ("INSUFFICIENT_FUNDS".equals(errorCode) || "INSUFFICIENT_FUNDS".equals(status)) {
            throw new InsufficientFundsException(errorMessage);
        }

        if (!"SUCCESS".equals(status) && !"OK".equals(status)) {
            String resolvedErrorCode = (errorCode == null || errorCode.isBlank()) ? "VALIDATION_ERROR" : errorCode;
            throw new OneCException(errorMessage, resolvedErrorCode);
        }

        if (responseDTO.getExternalId() == null || responseDTO.getExternalId().isBlank()) {
            throw new OneCException("1C response does not contain externalId", "EMPTY_EXTERNAL_ID");
        }
    }

    @Transactional
    public boolean manualRetry(Long syncId) {
        return syncHistoryRepository.findById(syncId)
            .map(syncRecord -> {
                retrySync(syncRecord);
                return true;
            })
            .orElse(false);
    }

    @Transactional
    public boolean reviewRejection(Long rejectionId, ReviewRejectionRequestDTO request) {
        return rejectedApplicationRepository.findById(rejectionId)
            .map(rejection -> {
                rejection.setManualReviewRequired(false);
                rejection.setReviewedBy(request.getReviewedBy());
                rejection.setReviewedAt(LocalDateTime.now());
                rejectedApplicationRepository.save(rejection);
                return true;
            })
            .orElse(false);
    }

    private OneCyncHistoryDTO mapToSyncHistoryDTO(OneCyncHistory entity) {
        return OneCyncHistoryDTO.builder()
            .id(entity.getId())
            .applicationId(entity.getApplication().getId())
            .externalId(entity.getExternalId())
            .syncStatus(entity.getSyncStatus())
            .syncDirection(entity.getSyncDirection().toString())
            .retryCount(entity.getRetryCount())
            .maxRetries(entity.getMaxRetries())
            .lastError(entity.getLastError())
            .lastSyncAt(entity.getLastSyncAt())
            .nextRetryAt(entity.getNextRetryAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private RejectedApplicationDTO mapToRejectedApplicationDTO(RejectedApplication entity) {
        return RejectedApplicationDTO.builder()
            .id(entity.getId())
            .applicationId(entity.getApplication().getId())
            .syncHistoryId(entity.getSyncHistory() != null ? entity.getSyncHistory().getId() : null)
            .rejectionReason(entity.getRejectionReason())
            .errorCode(entity.getErrorCode())
            .manualReviewRequired(entity.getManualReviewRequired())
            .reviewedAt(entity.getReviewedAt())
            .reviewedBy(entity.getReviewedBy())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}

