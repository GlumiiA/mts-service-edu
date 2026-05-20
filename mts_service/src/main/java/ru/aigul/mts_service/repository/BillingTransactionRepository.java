package ru.aigul.mts_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.aigul.mts_service.model.BillingTransaction;

public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, Long> {
}
