package ru.aigul.mts_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.aigul.mts_service.model.OneCIntegrationStatus;

import java.time.LocalDateTime;

/**
 * DTO for API responses about sync history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCyncHistoryDTO {
    
    private Long id;
    private Long applicationId;
    private String externalId;
    private OneCIntegrationStatus syncStatus;
    private String syncDirection;
    private Integer retryCount;
    private Integer maxRetries;
    private String lastError;
    private LocalDateTime lastSyncAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
