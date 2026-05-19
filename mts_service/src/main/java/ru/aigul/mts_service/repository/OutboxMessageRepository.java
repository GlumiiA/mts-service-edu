package ru.aigul.mts_service.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.OutboxMessage;
import ru.aigul.mts_service.model.OutboxMessageStatus;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM OutboxMessage o
            WHERE o.status = :status
              AND o.nextAttemptAt <= :now
            ORDER BY o.createdAt ASC
            """)
    List<OutboxMessage> findDueForDispatch(@Param("status") OutboxMessageStatus status,
                                           @Param("now") OffsetDateTime now,
                                           Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxMessage o WHERE o.id = :id")
    Optional<OutboxMessage> findByIdForUpdate(@Param("id") Long id);

    List<OutboxMessage> findByStatusAndLockedAtBefore(OutboxMessageStatus status, OffsetDateTime lockedAt);
}
