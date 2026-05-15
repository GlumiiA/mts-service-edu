package ru.aigul.mts_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.OutboxMessage;
import ru.aigul.mts_service.model.OutboxMessageStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, String> {

    @Query("""
            SELECT o FROM OutboxMessage o
            WHERE o.status IN :statuses
              AND o.nextAttemptAt <= :now
            ORDER BY o.createdAt ASC
            """)
    List<OutboxMessage> findReadyToDispatch(@Param("statuses") Collection<OutboxMessageStatus> statuses,
                                            @Param("now") OffsetDateTime now,
                                            org.springframework.data.domain.Pageable pageable);
}

