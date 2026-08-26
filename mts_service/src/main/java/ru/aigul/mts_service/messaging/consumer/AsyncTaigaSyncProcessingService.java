package ru.aigul.mts_service.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.messaging.dto.TaigaStoryRequestedMessage;
import ru.aigul.mts_service.messaging.inbox.MessageInboxService;
import ru.aigul.mts_service.service.ApplicationTaigaSyncWorkflowService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaigaSyncProcessingService {

    private final ApplicationTaigaSyncWorkflowService taigaSyncWorkflowService;
    private final MessageInboxService messageInboxService;

    @Transactional
    public void process(TaigaStoryRequestedMessage message, Integer deliveryCount) {
        if (!messageInboxService.register(message.messageId(), "taiga-sync-listener")) {
            log.info("Duplicate Taiga sync message skipped: messageId={}, applicationId={}",
                    message.messageId(), message.applicationId());
            return;
        }

        int attempt = deliveryCount != null ? deliveryCount : 1;
        log.info("Received TaigaStoryRequested message: applicationId={}, attempt={}",
                message.applicationId(), attempt);
        taigaSyncWorkflowService.createStoryForApplication(message.applicationId(), attempt);
    }
}
