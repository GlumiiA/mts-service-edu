package ru.aigul.mts_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for marking application as reviewed
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRejectionRequestDTO {
    
    private String reviewedBy;
    private String notes;
    private Boolean approve; // true = approve, false = keep rejected
}
