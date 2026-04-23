package ru.aigul.mts_service.dto.demo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplicationStateDto {
    private Long id;
    private String status;
    private String tariffName;
    private BigDecimal totalPrice;
    private Long userId;
    private final String source = "mts_db";
}