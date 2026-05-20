package ru.aigul.mts_service.messaging.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class MessageInboxCleanupJob {

    private final MessageInboxService messageInboxService;

    public void cleanupExpired() {
        messageInboxService.cleanupExpired(OffsetDateTime.now());
    }
}
