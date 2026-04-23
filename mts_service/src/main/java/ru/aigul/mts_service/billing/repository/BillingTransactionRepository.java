package ru.aigul.mts_service.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.aigul.mts_service.billing.model.BillingTransaction;

import java.util.List;

public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, Long> {
    List<BillingTransaction> findByApplicationId(Long applicationId);
    List<BillingTransaction> findByUserId(Long userId);
}