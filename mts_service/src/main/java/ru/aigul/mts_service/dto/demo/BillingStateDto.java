package ru.aigul.mts_service.dto.demo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BillingStateDto {
    private BigDecimal balance;
    private List<TransactionDto> transactions;
    private final String source = "billing_db";
}