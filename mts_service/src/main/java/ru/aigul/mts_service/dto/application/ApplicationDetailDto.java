package ru.aigul.mts_service.dto.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.aigul.mts_service.dto.catalog.ServiceDto;
import ru.aigul.mts_service.model.ApplicationStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailDto {
    private Long id;
    private Long userId;
    private Long tariffId;
    private String tariffName;
    private String address;
    private ApplicationStatus status;
    private String rejectReason;
    private Long taigaTaskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ServiceDto> additionalServices;
    private Boolean passportVerified;
    private Boolean technicalFeasibility;
}
