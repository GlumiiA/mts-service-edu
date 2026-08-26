package ru.aigul.mts_service.mapper;

import org.springframework.stereotype.Component;
import ru.aigul.mts_service.dto.application.ApplicationCreateDto;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.model.Tariff;
import ru.aigul.mts_service.model.User;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Component
public class ApplicationEntityMapper {

    public Application fromCreateDto(User user,
                                     Tariff tariff,
                                     ApplicationCreateDto dto,
                                     BigDecimal lockedPrice,
                                     Set<ru.aigul.mts_service.model.Service> additionalServices) {
        Application application = new Application();
        application.setUser(user);
        application.setTariff(tariff);
        application.setAddress(dto.getAddress());
        application.setStatus(ApplicationStatus.PENDING_TAIGA_SYNC);
        application.setLockedPrice(lockedPrice != null ? lockedPrice : BigDecimal.ZERO);
        application.setAdditionalServices(additionalServices != null ? additionalServices : new HashSet<>());
        return application;
    }
}

