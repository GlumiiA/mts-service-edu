package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.billing.model.Balance;
import ru.aigul.mts_service.billing.model.BillingTransaction;
import ru.aigul.mts_service.billing.model.TransactionType;
import ru.aigul.mts_service.billing.repository.BalanceRepository;
import ru.aigul.mts_service.billing.repository.BillingTransactionRepository;
import ru.aigul.mts_service.dto.application.ApplicationDto;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.exception.InsufficientFundsException;
import ru.aigul.mts_service.exception.InvalidApplicationStatusException;
import ru.aigul.mts_service.mapper.ApplicationMapper;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.repository.ApplicationRepository;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationApprovalWorkflowService {

    private final ApplicationRepository applicationRepository;
    private final BalanceRepository balanceRepository;
    private final BillingTransactionRepository billingTransactionRepository;
    private final ApplicationMapper applicationMapper;
    private final OutboxService outboxService;

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

        Long userId = application.getUser().getId();
        Balance balance = balanceRepository.findByUserIdForUpdate(userId)
                .orElseThrow(InsufficientFundsException::new);

        BigDecimal totalPrice = application.getLockedPrice();
        if (balance.getAmount().compareTo(totalPrice) < 0) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectReason("Недостаточно средств");
            applicationRepository.save(application);

            log.info("Application {} rejected asynchronously because of insufficient funds", applicationId);
            return applicationMapper.toDto(application);
        }

        balance.setAmount(balance.getAmount().subtract(totalPrice));
        balanceRepository.save(balance);

        BillingTransaction billingTx = new BillingTransaction();
        billingTx.setUserId(userId);
        billingTx.setApplicationId(applicationId);
        billingTx.setAmount(totalPrice);
        billingTx.setType(TransactionType.DEBIT);

        StringBuilder description = new StringBuilder()
                .append("Payment for application #").append(applicationId)
                .append(", tariff: ").append(application.getTariff().getName());
        if (correlationId != null && !correlationId.isBlank()) {
            description.append(", correlationId=").append(correlationId);
        }
        if (requestedBy != null && !requestedBy.isBlank()) {
            description.append(", requestedBy=").append(requestedBy);
        }
        billingTx.setDescription(description.toString());
        billingTransactionRepository.save(billingTx);

        application.setStatus(ApplicationStatus.APPROVED);
        applicationRepository.save(application);
        outboxService.enqueueConnectionRequested(applicationId, correlationId);

        log.info("Application {} approved successfully{}", applicationId,
                correlationId != null ? " correlationId=" + correlationId : "");
        return applicationMapper.toDto(application);
    }
}
