package ru.aigul.mts_service.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.aigul.mts_service.dto.OneCyncHistoryDTO;
import ru.aigul.mts_service.dto.RejectedApplicationDTO;
import ru.aigul.mts_service.dto.ReviewRejectionRequestDTO;
import ru.aigul.mts_service.model.*;
import ru.aigul.mts_service.repository.OneCErrorRepository;
import ru.aigul.mts_service.repository.OneCyncHistoryRepository;
import ru.aigul.mts_service.repository.RejectedApplicationRepository;
import ru.aigul.mts_service.service.integration.OneCIntegrationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/one-c")
@Slf4j
@RequiredArgsConstructor
public class OneCIntegrationController {

    private final OneCyncHistoryRepository syncHistoryRepository;
    private final OneCErrorRepository errorRepository;
    private final RejectedApplicationRepository rejectedApplicationRepository;
    private final OneCIntegrationService integrationService;

    @GetMapping("/sync/application/{applicationId}")
    public ResponseEntity<?> getSyncHistory(
            @PathVariable Long applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            log.info("Fetching sync history for application id={}", applicationId);
            
            Page<OneCyncHistory> syncHistory = syncHistoryRepository.findByApplicationId(
                applicationId, 
                org.springframework.data.domain.PageRequest.of(page, size)
            );
            
            Page<OneCyncHistoryDTO> result = syncHistory.map(this::mapToDTO);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error fetching sync history", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/sync/external/{externalId}")
    public ResponseEntity<?> getSyncByExternalId(@PathVariable String externalId) {
        try {
            log.info("Fetching sync history for external_id={}", externalId);
            
            var syncHistory = syncHistoryRepository.findByExternalId(externalId);
            
            if (syncHistory.isPresent()) {
                return ResponseEntity.ok(mapToDTO(syncHistory.get()));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error fetching sync by external ID", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }


    @GetMapping("/rejected")
    public ResponseEntity<?> getRejectedApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            log.info("Fetching rejected applications");
            
            Page<RejectedApplication> rejections = rejectedApplicationRepository
                .findByManualReviewRequiredTrueOrderByCreatedAtDesc(
                    org.springframework.data.domain.PageRequest.of(page, size)
                );
            
            Page<RejectedApplicationDTO> result = rejections.map(this::mapToDTO);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error fetching rejected applications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }


    @GetMapping("/rejected/application/{applicationId}")
    public ResponseEntity<?> getApplicationRejections(@PathVariable Long applicationId) {
        try {
            log.info("Fetching rejections for application id={}", applicationId);
            
            List<RejectedApplication> rejections = rejectedApplicationRepository
                .findByApplicationId(applicationId);
            
            List<RejectedApplicationDTO> result = rejections.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error fetching application rejections", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }


    @GetMapping("/rejected/count")
    public ResponseEntity<?> getPendingReviewCount() {
        try {
            Long count = rejectedApplicationRepository.countByManualReviewRequiredTrue();
            
            return ResponseEntity.ok("{\"pendingReviewCount\": " + count + "}");
            
        } catch (Exception e) {
            log.error("Error fetching pending review count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }


    @GetMapping("/errors/stats")
    public ResponseEntity<?> getErrorStats() {
        try {
            log.info("Fetching error statistics");
            
            List<Object[]> stats = errorRepository.countErrorsByType();
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error fetching error statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }


    @PostMapping("/sync/{syncId}/retry")
    public ResponseEntity<?> manualRetry(@PathVariable Long syncId) {
        try {
            log.info("Manual retry triggered for sync id={}", syncId);
            
            var syncRecord = syncHistoryRepository.findById(syncId);
            
            if (syncRecord.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            OneCyncHistory sync = syncRecord.get();
            integrationService.retrySync(sync);
            
            return ResponseEntity.ok("{\"message\": \"Retry scheduled\"}");
            
        } catch (Exception e) {
            log.error("Error triggering manual retry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/rejected/{rejectionId}/review")
    public ResponseEntity<?> reviewRejection(
            @PathVariable Long rejectionId,
            @RequestBody ReviewRejectionRequestDTO request) {
        
        try {
            log.info("Reviewing rejection id={} by user={}", rejectionId, request.getReviewedBy());
            
            var rejection = rejectedApplicationRepository.findById(rejectionId);
            
            if (rejection.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            RejectedApplication rejectedApp = rejection.get();
            rejectedApp.setManualReviewRequired(false);
            rejectedApp.setReviewedBy(request.getReviewedBy());
            rejectedApp.setReviewedAt(LocalDateTime.now());
            
            rejectedApplicationRepository.save(rejectedApp);
            
            return ResponseEntity.ok("{\"message\": \"Rejection reviewed\"}");
            
        } catch (Exception e) {
            log.error("Error reviewing rejection", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private OneCyncHistoryDTO mapToDTO(OneCyncHistory entity) {
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

    private RejectedApplicationDTO mapToDTO(RejectedApplication entity) {
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
