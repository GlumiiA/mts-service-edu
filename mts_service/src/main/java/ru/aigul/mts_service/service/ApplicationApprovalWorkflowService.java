package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.dto.application.ApplicationDto;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.exception.InsufficientFundsException;
import ru.aigul.mts_service.exception.InvalidApplicationStatusException;
import ru.aigul.mts_service.mapper.ApplicationMapper;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;
import ru.aigul.mts_service.messaging.outbox.OutboxService;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.repository.ApplicationRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationApprovalWorkflowService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final OutboxService outboxService;
    private final LocalBillingService localBillingService;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ApplicationDto approveAsynchronously(Long applicationId, String requestedBy, String correlationId) {
        return approve(applicationId, requestedBy, correlationId);
    }

    private ApplicationDto approve(Long applicationId,
                                   String requestedBy,
                                   String correlationId) {
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (application.getStatus() == ApplicationStatus.APPROVED
                || application.getStatus() == ApplicationStatus.CONNECTED) {
            log.info("Application {} is already processed with status {}", applicationId, application.getStatus());
            return applicationMapper.toDto(application);
        }

        if (application.getStatus() == ApplicationStatus.REJECTED) {
            throw new InvalidApplicationStatusException("Application already rejected");
        }

        if (application.getStatus() == ApplicationStatus.PROCESSING) {
            log.info("Application {} is already processing, returning current state", applicationId);
            return applicationMapper.toDto(application);
        }

        application.setStatus(ApplicationStatus.PROCESSING);
        applicationRepository.save(application);

        BigDecimal totalPrice = application.getLockedPrice();
        try {
            localBillingService.debit(
                    application.getUser(),
                    applicationId,
                    totalPrice,
                    buildDebitNote(applicationId, requestedBy, correlationId, application)
            );
        } catch (InsufficientFundsException ex) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectReason("Not enough funds");
            applicationRepository.save(application);

            log.info("Application {} rejected asynchronously because of insufficient funds", applicationId);
            return applicationMapper.toDto(application);
        }

        application.setStatus(ApplicationStatus.APPROVED);
        applicationRepository.save(application);
        outboxService.enqueueConnectionRequested(new ConnectionRequestedMessage(
                UUID.randomUUID().toString(),
                applicationId,
                correlationId,
                OffsetDateTime.now()
        ));

        log.info("Application {} approved successfully{}", applicationId,
                correlationId != null ? " correlationId=" + correlationId : "");
        return applicationMapper.toDto(application);
    }

    private String buildDebitNote(Long applicationId,
                                  String requestedBy,
                                  String correlationId,
                                  Application application) {
        StringBuilder description = new StringBuilder()
                .append("Payment for application #").append(applicationId)
                .append(", tariff: ").append(application.getTariff().getName());
        if (correlationId != null && !correlationId.isBlank()) {
            description.append(", correlationId=").append(correlationId);
        }
        if (requestedBy != null && !requestedBy.isBlank()) {
            description.append(", requestedBy=").append(requestedBy);
        }
        return description.toString();
    }
}
