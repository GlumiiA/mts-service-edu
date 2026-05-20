package ru.aigul.mts_service.messaging.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.model.OutboxMessage;
import ru.aigul.mts_service.model.OutboxMessageStatus;
import ru.aigul.mts_service.repository.OutboxMessageRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxMessageStateService {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final OutboxMessageRepository outboxMessageRepository;

    @Transactional
    public List<DispatchItem> claimBatch() {
        List<OutboxMessage> dueMessages = outboxMessageRepository.findDueForDispatch(
                OutboxMessageStatus.NEW,
                OffsetDateTime.now(),
                PageRequest.of(0, BATCH_SIZE)
        );

        if (dueMessages.isEmpty()) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<DispatchItem> items = new ArrayList<>(dueMessages.size());
        for (OutboxMessage message : dueMessages) {
            message.setStatus(OutboxMessageStatus.PROCESSING);
            message.setLockedAt(now);
            message.setAttempts(message.getAttempts() + 1);
            items.add(new DispatchItem(
                    message.getId(),
                    message.getMessageId(),
                    message.getDestination(),
                    message.getPayloadType(),
                    message.getPayloadJson(),
                    message.getAttempts()
            ));
        }
        return items;
    }

    @Transactional
    public void recoverStaleProcessing() {
        OffsetDateTime staleBefore = OffsetDateTime.now().minus(PROCESSING_TIMEOUT);
        List<OutboxMessage> staleMessages = outboxMessageRepository.findByStatusAndLockedAtBefore(
                OutboxMessageStatus.PROCESSING,
                staleBefore
        );

        for (OutboxMessage message : staleMessages) {
            message.setStatus(OutboxMessageStatus.NEW);
            message.setLockedAt(null);
            message.setNextAttemptAt(OffsetDateTime.now());
            message.setLastError("Recovered from stale PROCESSING state");
        }
    }

    @Transactional
    public void markSent(Long id) {
        OutboxMessage message = outboxMessageRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Outbox message not found: " + id));
        message.setStatus(OutboxMessageStatus.SENT);
        message.setSentAt(OffsetDateTime.now());
        message.setLockedAt(null);
        message.setLastError(null);
    }

    @Transactional
    public void handleFailure(Long id, int attempts, Exception ex) {
        OutboxMessage message = outboxMessageRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalStateException("Outbox message not found: " + id));

        String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        message.setLockedAt(null);
        message.setLastError(error);

        if (attempts >= MAX_ATTEMPTS) {
            message.setStatus(OutboxMessageStatus.FAILED);
            return;
        }

        message.setStatus(OutboxMessageStatus.NEW);
        message.setNextAttemptAt(OffsetDateTime.now().plusSeconds(nextBackoffSeconds(attempts)));
    }

    private long nextBackoffSeconds(int attempts) {
        long base = 10L;
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 6);
        return Math.min(base * multiplier, 900L);
    }

    public record DispatchItem(
            Long id,
            String messageId,
            String destination,
            String payloadType,
            String payloadJson,
            int attempts
    ) {
    }
}
