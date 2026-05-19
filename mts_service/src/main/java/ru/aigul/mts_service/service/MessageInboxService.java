package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.repository.MessageInboxRepository;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class MessageInboxService {

    private final MessageInboxRepository messageInboxRepository;

    @Value("${app.messaging.inbox.retention-hours:24}")
    private long retentionHours;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean register(String messageId, String consumer) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        if (consumer == null || consumer.isBlank()) {
            throw new IllegalArgumentException("consumer must not be blank");
        }

        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(retentionHours);
        return messageInboxRepository.insertIfAbsent(messageId, consumer, expiresAt) == 1;
    }

    @Transactional
    public int cleanupExpired(OffsetDateTime now) {
        return messageInboxRepository.deleteExpiredBefore(now);
    }
}
