package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class MessageInboxCleanupJob {

    private final MessageInboxService messageInboxService;

    @Scheduled(fixedDelayString = "${app.messaging.inbox.cleanup-delay-ms:3600000}")
    public void cleanupExpired() {
        messageInboxService.cleanupExpired(OffsetDateTime.now());
    }
}
