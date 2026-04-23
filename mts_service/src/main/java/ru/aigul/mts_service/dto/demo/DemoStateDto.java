package ru.aigul.mts_service.dto.demo;

import lombok.Data;

@Data
public class DemoStateDto {
    private ApplicationStateDto application;
    private BillingStateDto billing;
    private boolean consistent;
}