package ru.aigul.mts_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for API responses about rejected applications
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectedApplicationDTO {
    
    private Long id;
    private Long applicationId;
    private Long syncHistoryId;
    private String rejectionReason;
    private String errorCode;
    private Boolean manualReviewRequired;
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    private LocalDateTime createdAt;
}
