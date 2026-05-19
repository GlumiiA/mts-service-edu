package ru.aigul.mts_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.MessageInbox;

import java.time.OffsetDateTime;

public interface MessageInboxRepository extends JpaRepository<MessageInbox, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO message_inbox (message_id, consumer, expires_at)
            VALUES (:messageId, :consumer, :expiresAt)
            ON CONFLICT (message_id, consumer) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") String messageId,
                       @Param("consumer") String consumer,
                       @Param("expiresAt") OffsetDateTime expiresAt);

    @Modifying
    @Query("DELETE FROM MessageInbox m WHERE m.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") OffsetDateTime now);
}
