package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.billing.model.Balance;
import ru.aigul.mts_service.billing.repository.BalanceRepository;
import ru.aigul.mts_service.dto.CursorPage;
import ru.aigul.mts_service.dto.application.*;
import ru.aigul.mts_service.messaging.dto.ApprovalRequestedMessage;
import ru.aigul.mts_service.exception.*;
import ru.aigul.mts_service.mapper.ApplicationMapper;
import ru.aigul.mts_service.model.*;
import ru.aigul.mts_service.repository.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final TariffRepository tariffRepository;
    private final TariffCityPriceRepository tariffCityPriceRepository;
    private final BalanceRepository balanceRepository;
    private final ServiceRepository serviceRepository;
    private final ApplicationMapper applicationMapper;
    private final UserService userService;
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
        Long userId = user.getId();

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

        Balance balance = balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new InsufficientFundsException());
        if (balance.getAmount().compareTo(totalPrice) < 0) {
            throw new InsufficientFundsException();
        }

        Application application = new Application();
        application.setUser(user);
        application.setTariff(tariff);
        application.setAddress(dto.getAddress());
        application.setStatus(ApplicationStatus.PENDING);
        application.setLockedPrice(totalPrice);
        application.setAdditionalServices(new HashSet<>(additionalServices));

        application = applicationRepository.save(application);
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

    @Transactional
    public String requestApproveAsync(Long applicationId, String requestedBy) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new InvalidApplicationStatusException("Application already processed");
        }

        String correlationId = UUID.randomUUID().toString();
        outboxService.enqueueApprovalRequested(new ApprovalRequestedMessage(
                UUID.randomUUID().toString(),
                applicationId,
                requestedBy,
                correlationId,
                OffsetDateTime.now()
        ));
        return correlationId;
    }
}
