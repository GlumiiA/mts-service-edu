package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.aigul.mts_service.billing.model.Balance;
import ru.aigul.mts_service.billing.repository.BalanceRepository;
import ru.aigul.mts_service.dto.BalanceResponse;
import ru.aigul.mts_service.model.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserService userService;
    private final BalanceRepository balanceRepository;

    @Value("${payments.base-url}")
    private String paymentsBaseUrl;

    @Value("${app.currency.code}")
    private String currencyCode;

    public Optional<Balance> findBalanceForUserEmail(String email) {
        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        return balanceRepository.findByUserId(user.getId());
    }

    public Optional<String> createTopUpPayment(String email, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        String paymentId = UUID.randomUUID().toString();
        String base = paymentsBaseUrl;
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String paymentUrl = base + paymentId;

        log.debug("PaymentUrl: {}", paymentUrl);

        return Optional.of(paymentUrl);
    }

    public BalanceResponse getBalanceResponseForUserEmail(String email) {
        Optional<Balance> balanceOpt = findBalanceForUserEmail(email);

        if (balanceOpt.isEmpty()) {
            return new BalanceResponse(BigDecimal.ZERO, currencyCode, OffsetDateTime.now(ZoneOffset.UTC));
        }

        Balance b = balanceOpt.get();
        OffsetDateTime updated = b.getUpdatedAt() != null ? b.getUpdatedAt().atOffset(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal amount = b.getAmount() != null ? b.getAmount() : BigDecimal.ZERO;
        return new BalanceResponse(amount, currencyCode, updated);
    }
}