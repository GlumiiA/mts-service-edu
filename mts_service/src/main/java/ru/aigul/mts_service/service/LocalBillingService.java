package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;
import ru.aigul.mts_service.exception.InsufficientFundsException;
import ru.aigul.mts_service.model.BillingBalance;
import ru.aigul.mts_service.model.BillingTransaction;
import ru.aigul.mts_service.model.User;
import ru.aigul.mts_service.repository.BillingBalanceRepository;
import ru.aigul.mts_service.repository.BillingTransactionRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class LocalBillingService {

    private static final String TYPE_DEBIT = "DEBIT";
    private static final String TYPE_TOP_UP_REQUEST = "TOP_UP_REQUEST";

    private final BillingBalanceRepository billingBalanceRepository;
    private final BillingTransactionRepository billingTransactionRepository;

    @Value("${payments.base-url:https://payments.example.com/mock-pay}")
    private String paymentsBaseUrl;

    @Transactional(readOnly = true)
    public BigDecimal getBalance(User user) {
        return billingBalanceRepository.findByUserId(user.getId())
                .map(BillingBalance::getAmount)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public String createTopUpPayment(User user, BigDecimal amount) {
        BillingBalance balance = findForUpdateOrCreate(user.getId());
        if (balance.getAmount() == null) {
            balance.setAmount(BigDecimal.ZERO);
        }
        billingBalanceRepository.save(balance);

        BillingTransaction tx = new BillingTransaction();
        tx.setUserId(user.getId());
        tx.setApplicationId(0L);
        tx.setAmount(amount);
        tx.setType(TYPE_TOP_UP_REQUEST);
        tx.setDescription("Top-up request created");
        billingTransactionRepository.save(tx);

        return paymentsBaseUrl
                + "?userId=" + user.getId()
                + "&amount=" + UriUtils.encodeQueryParam(amount.toPlainString(), StandardCharsets.UTF_8);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void debit(User user,
                      Long applicationId,
                      BigDecimal amount,
                      String description) {
        BillingBalance balance = findForUpdateOrCreate(user.getId());
        BigDecimal current = balance.getAmount() == null ? BigDecimal.ZERO : balance.getAmount();
        if (current.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        balance.setAmount(current.subtract(amount));
        billingBalanceRepository.save(balance);

        BillingTransaction tx = new BillingTransaction();
        tx.setUserId(user.getId());
        tx.setApplicationId(applicationId);
        tx.setAmount(amount);
        tx.setType(TYPE_DEBIT);
        tx.setDescription(description);
        billingTransactionRepository.save(tx);
    }

    private BillingBalance findForUpdateOrCreate(Long userId) {
        return billingBalanceRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> {
                    BillingBalance created = new BillingBalance();
                    created.setUserId(userId);
                    created.setAmount(BigDecimal.ZERO);
                    return billingBalanceRepository.save(created);
                });
    }
}
