package ru.aigul.mts_service.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.aigul.mts_service.billing.model.Balance;

import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, Long> {
    Optional<Balance> findByUserId(Long userId);
}