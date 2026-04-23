package ru.aigul.mts_service.dto.demo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionDto {
    private Long id;
    private String type;
    private BigDecimal amount;
    private Long applicationId;
    private String description;
    private LocalDateTime createdAt;
}