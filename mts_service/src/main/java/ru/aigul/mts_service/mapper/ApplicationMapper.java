package ru.aigul.mts_service.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.dto.application.ApplicationDetailDto;
import ru.aigul.mts_service.dto.application.ApplicationDto;
import ru.aigul.mts_service.dto.catalog.ServiceDto;
import ru.aigul.mts_service.model.Application;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApplicationMapper {

    private final ServiceMapper serviceMapper;

    public ApplicationDto toDto(Application a) {
        return new ApplicationDto(
                a.getId(),
                a.getUser().getId(),
                a.getTariff().getId(),
                a.getTariff().getName(),
                a.getAddress(),
                a.getStatus(),
                a.getRejectReason(),
                a.getTaigaTaskId(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }

    public ApplicationDetailDto toDetailDto(Application a) {
        List<ServiceDto> services = a.getAdditionalServices().stream()
                .map(serviceMapper::toDto)
                .toList();
        return new ApplicationDetailDto(
                a.getId(),
                a.getUser().getId(),
                a.getTariff().getId(),
                a.getTariff().getName(),
                a.getAddress(),
                a.getStatus(),
                a.getRejectReason(),
                a.getTaigaTaskId(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                services,
                Boolean.TRUE.equals(a.getPassportVerified()),
                Boolean.TRUE.equals(a.getTechnicalFeasibility())
        );
    }
}
