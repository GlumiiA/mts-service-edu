package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.dto.BalanceResponse;
import ru.aigul.mts_service.model.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserService userService;
    private final LocalBillingService localBillingService;

    @Value("${app.currency.code:RUB}")
    private String currencyCode;

    public Optional<BigDecimal> findBalanceForUserEmail(String email) {
        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        return Optional.of(localBillingService.getBalance(user));
    }

    public Optional<String> createTopUpPayment(String email, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        String paymentUrl = localBillingService.createTopUpPayment(user, amount);
        return Optional.of(paymentUrl);
    }

    public BalanceResponse getBalanceResponseForUserEmail(String email) {
        Optional<BigDecimal> balanceOpt = findBalanceForUserEmail(email);
        BigDecimal amount = balanceOpt.orElse(BigDecimal.ZERO);
        return new BalanceResponse(amount, currencyCode, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
