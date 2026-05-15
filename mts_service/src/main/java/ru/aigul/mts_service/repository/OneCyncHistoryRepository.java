package ru.aigul.mts_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.OneCyncHistory;
import ru.aigul.mts_service.model.OneCIntegrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OneCyncHistoryRepository extends JpaRepository<OneCyncHistory, Long> {

    /**
     * Find sync history by external 1C ID (unique identifier from 1C)
     */
    Optional<OneCyncHistory> findByExternalId(String externalId);

    /**
     * Find all pending syncs ready for retry
     */
    @Query("SELECT h FROM OneCyncHistory h WHERE h.syncStatus IN ('PENDING', 'RETRY') AND h.nextRetryAt <= :now ORDER BY h.nextRetryAt ASC")
    List<OneCyncHistory> findPendingAndReadyForRetry(@Param("now") LocalDateTime now);

    /**
     * Find sync history by application ID
     */
    Page<OneCyncHistory> findByApplicationId(Long applicationId, Pageable pageable);

    /**
     * Find all syncs with specific status
     */
    List<OneCyncHistory> findBySyncStatus(OneCIntegrationStatus status);

    /**
     * Find syncs that failed with insufficient funds
     */
    @Query("SELECT h FROM OneCyncHistory h WHERE h.syncStatus = 'INSUFFICIENT_FUNDS' ORDER BY h.updatedAt DESC")
    List<OneCyncHistory> findInsufficientFundsFailures();

    /**
     * Find syncs sent to DLQ (Dead Letter Queue)
     */
    @Query("SELECT h FROM OneCyncHistory h WHERE h.syncStatus = 'DLQ' ORDER BY h.updatedAt DESC")
    List<OneCyncHistory> findDLQMessages();

    /**
     * Count retry attempts for specific time period
     */
    @Query("SELECT COUNT(h) FROM OneCyncHistory h WHERE h.createdAt >= :startTime AND h.retryCount > 0")
    Long countRetriesInPeriod(@Param("startTime") LocalDateTime startTime);
}
