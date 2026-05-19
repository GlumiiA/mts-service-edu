package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.billing.model.Balance;
import ru.aigul.mts_service.billing.model.BillingTransaction;
import ru.aigul.mts_service.billing.repository.BalanceRepository;
import ru.aigul.mts_service.billing.repository.BillingTransactionRepository;
import ru.aigul.mts_service.dto.demo.*;
import ru.aigul.mts_service.exception.ApplicationNotFoundException;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.ApplicationStatus;
import ru.aigul.mts_service.repository.ApplicationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DemoService {

    private final ApplicationRepository applicationRepository;
    private final BalanceRepository balanceRepository;
    private final BillingTransactionRepository billingTransactionRepository;

    @Transactional(readOnly = true)
    public DemoStateDto getState(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        return buildState(app);
    }

    @Transactional(readOnly = true)
    public List<DemoStateDto> getAllStates() {
        return applicationRepository.findAll().stream()
                .map(this::buildState)
                .toList();
    }

    private DemoStateDto buildState(Application app) {
        DemoStateDto state = new DemoStateDto();

        ApplicationStateDto appState = new ApplicationStateDto();
        appState.setId(app.getId());
        appState.setStatus(app.getStatus().name());
        appState.setTariffName(app.getTariff().getName());

        appState.setTotalPrice(app.getLockedPrice());
        appState.setUserId(app.getUser().getId());
        state.setApplication(appState);

        BillingStateDto billingState = new BillingStateDto();
        Optional<Balance> balanceOpt = balanceRepository.findByUserId(app.getUser().getId());
        billingState.setBalance(balanceOpt.map(Balance::getAmount).orElse(BigDecimal.ZERO));

        List<BillingTransaction> txs = billingTransactionRepository.findByApplicationId(app.getId());
        billingState.setTransactions(txs.stream().map(tx -> {
            TransactionDto dto = new TransactionDto();
            dto.setId(tx.getId());
            dto.setType(tx.getType().name());
            dto.setAmount(tx.getAmount());
            dto.setApplicationId(tx.getApplicationId());
            dto.setDescription(tx.getDescription());
            dto.setCreatedAt(tx.getCreatedAt());
            return dto;
        }).toList());
        state.setBilling(billingState);

        boolean consistent;
        boolean hasDebitTx = txs.stream()
                .anyMatch(tx -> tx.getType() == ru.aigul.mts_service.billing.model.TransactionType.DEBIT);

        if (app.getStatus() == ApplicationStatus.APPROVED || app.getStatus() == ApplicationStatus.CONNECTED) {
            consistent = hasDebitTx;
        } else if (app.getStatus() == ApplicationStatus.PENDING
                || app.getStatus() == ApplicationStatus.PROCESSING
                || app.getStatus() == ApplicationStatus.REJECTED
                || app.getStatus() == ApplicationStatus.FAILED_EXTERNAL) {
            consistent = !hasDebitTx;
        } else {
            consistent = true;
        }
        state.setConsistent(consistent);

        return state;
    }
}
