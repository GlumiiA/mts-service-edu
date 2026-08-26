package ru.aigul.mts_service.dto.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.aigul.mts_service.model.ApplicationStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDto {
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
}
