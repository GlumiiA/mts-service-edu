package ru.aigul.mts_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.ErrorDetailEnum;
import ru.aigul.mts_service.model.OneCError;

import java.time.LocalDateTime;
import java.util.List;

public interface OneCErrorRepository extends JpaRepository<OneCError, Long> {

    /**
     * Find errors for specific sync history
     */
    List<OneCError> findBySyncHistoryId(Long syncHistoryId);

    /**
     * Find errors of specific type
     */
    List<OneCError> findByErrorDetail(ErrorDetailEnum errorDetail);

    /**
     * Find all insufficient funds errors in time period
     */
    @Query("SELECT e FROM OneCError e WHERE e.errorDetail = 'INSUFFICIENT_FUNDS' AND e.createdAt >= :startTime ORDER BY e.createdAt DESC")
    Page<OneCError> findInsufficientFundsErrorsInPeriod(@Param("startTime") LocalDateTime startTime, Pageable pageable);

    /**
     * Find all network errors in time period
     */
    @Query("SELECT e FROM OneCError e WHERE e.errorDetail IN ('NETWORK_ERROR', 'TIMEOUT') AND e.createdAt >= :startTime ORDER BY e.createdAt DESC")
    Page<OneCError> findNetworkErrorsInPeriod(@Param("startTime") LocalDateTime startTime, Pageable pageable);

    /**
     * Group errors by type and count
     */
    @Query("SELECT e.errorDetail, COUNT(e) as errorCount FROM OneCError e GROUP BY e.errorDetail ORDER BY errorCount DESC")
    List<Object[]> countErrorsByType();
}
