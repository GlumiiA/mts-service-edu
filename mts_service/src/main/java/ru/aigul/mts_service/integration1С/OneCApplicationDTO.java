package ru.aigul.mts_service.integration1С;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCApplicationDTO {
    
    private Long applicationId;
    private Long userId;
    private String userName;
    private Long tariffId;
    private String address;
    private BigDecimal price;
    private String status;
    private String requestId; // For idempotency
    private LocalDateTime timestamp;
}
