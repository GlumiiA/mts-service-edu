package ru.aigul.mts_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.aigul.mts_service.model.BillingTransaction;

import java.util.List;

public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, Long> {
    List<BillingTransaction> findAllByApplicationIdOrderByCreatedAtAsc(Long applicationId);

    boolean existsByApplicationIdAndType(Long applicationId, String type);
}
