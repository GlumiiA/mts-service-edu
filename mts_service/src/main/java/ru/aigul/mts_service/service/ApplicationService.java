package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.dto.CursorPage;
import ru.aigul.mts_service.dto.application.*;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.exception.InsufficientFundsException;
import ru.aigul.mts_service.exception.InvalidApplicationStatusException;
import ru.aigul.mts_service.exception.TariffNotFoundException;
import ru.aigul.mts_service.exception.UserNotFoundException;
import ru.aigul.mts_service.mapper.ApplicationMapper;
import ru.aigul.mts_service.mapper.ApplicationEntityMapper;
import ru.aigul.mts_service.messaging.dto.TaigaStoryRequestedMessage;
import ru.aigul.mts_service.messaging.outbox.OutboxService;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.model.Tariff;
import ru.aigul.mts_service.model.TariffCityPrice;
import ru.aigul.mts_service.model.User;
import ru.aigul.mts_service.repository.ApplicationRepository;
import ru.aigul.mts_service.repository.ServiceRepository;
import ru.aigul.mts_service.repository.TariffCityPriceRepository;
import ru.aigul.mts_service.repository.TariffRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final TariffRepository tariffRepository;
    private final TariffCityPriceRepository tariffCityPriceRepository;
    private final ServiceRepository serviceRepository;
    private final ApplicationMapper applicationMapper;
    private final UserService userService;
    private final LocalBillingService localBillingService;
    private final ApplicationEntityMapper applicationEntityMapper;
    private final OutboxService outboxService;

    @Transactional(readOnly = true)
    public List<Application> getApplicationsForUserEmail(String email) {
        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Collections.emptyList();
        }
        User user = userOpt.get();

        return applicationRepository.findAllByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ApplicationDto create(String email, ApplicationCreateDto dto) {
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        Tariff tariff = tariffRepository.findById(dto.getTariffId())
                .orElseThrow(() -> new TariffNotFoundException(dto.getTariffId()));

        BigDecimal tariffPrice = tariff.getBasePrice();
        if (tariffPrice == null && dto.getCityId() != null) {
            tariffPrice = tariffCityPriceRepository
                    .findByTariffIdAndCityId(dto.getTariffId(), dto.getCityId())
                    .map(TariffCityPrice::getPrice)
                    .orElse(BigDecimal.ZERO);
        }
        if (tariffPrice == null) {
            tariffPrice = BigDecimal.ZERO;
        }
        BigDecimal totalPrice = tariffPrice;

        List<ru.aigul.mts_service.model.Service> additionalServices = List.of();
        List<Long> additionalIds = dto.getAdditionalServiceIds();
        if (additionalIds != null && !additionalIds.isEmpty()) {
            additionalServices = serviceRepository.findAllById(additionalIds);
            for (ru.aigul.mts_service.model.Service s : additionalServices) {
                totalPrice = totalPrice.add(s.getPrice());
            }
        }

        BigDecimal availableBalance = localBillingService.getBalance(user);
        if (availableBalance.compareTo(totalPrice) < 0) {
            throw new InsufficientFundsException();
        }

        Application application = applicationEntityMapper.fromCreateDto(
                user,
                tariff,
                dto,
                totalPrice,
                new HashSet<>(additionalServices)
        );

        application = applicationRepository.save(application);

        outboxService.enqueueTaigaStoryRequested(new TaigaStoryRequestedMessage(
                UUID.randomUUID().toString(), application.getId(), null, OffsetDateTime.now()));

        return applicationMapper.toDto(application);
    }

    @Transactional(readOnly = true)
    public CursorPage<ApplicationDto> list(Authentication auth, ApplicationStatus status, Long after, int limit) {
        Long userId = null;
        if (!auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("APPLICATION_READ_ALL"))) {
            userId = userService.findByEmail(auth.getName())
                    .orElseThrow(() -> new UserNotFoundException(auth.getName()))
                    .getId();
        }
        List<Application> raw = applicationRepository.findAllFiltered(userId, status, after, Limit.of(limit + 1));
        return CursorPage.of(raw, limit, applicationMapper::toDto, Application::getId);
    }

    @Transactional(readOnly = true)
    public ApplicationDetailDto getDetail(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        return applicationMapper.toDetailDto(application);
    }

    @Transactional
    public ApplicationDto reject(Long applicationId, ApplicationRejectDto dto) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new InvalidApplicationStatusException("Application already processed");
        }

        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectReason(dto.getReason());
        application = applicationRepository.save(application);
        return applicationMapper.toDto(application);
    }
}
