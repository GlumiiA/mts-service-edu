package ru.aigul.mts_service.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.BillingBalance;

import java.util.Optional;

public interface BillingBalanceRepository extends JpaRepository<BillingBalance, Long> {

    Optional<BillingBalance> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BillingBalance b WHERE b.userId = :userId")
    Optional<BillingBalance> findByUserIdForUpdate(@Param("userId") Long userId);
}
