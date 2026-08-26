package ru.aigul.mts_service.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final OutboxMessageStateService outboxMessageStateService;
    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public void dispatch() {
        while (true) {
            List<OutboxMessageStateService.DispatchItem> batch = outboxMessageStateService.claimBatch();
            if (batch.isEmpty()) {
                return;
            }

            for (OutboxMessageStateService.DispatchItem item : batch) {
                sendOne(item);
            }
        }
    }

    public void recoverStaleProcessing() {
        outboxMessageStateService.recoverStaleProcessing();
    }

    private void sendOne(OutboxMessageStateService.DispatchItem item) {
        try {
            Class<?> payloadClass = Class.forName(item.payloadType());
            Object payload = objectMapper.readValue(item.payloadJson(), payloadClass);
            jmsTemplate.convertAndSend(item.destination(), payload);
            try {
                outboxMessageStateService.markSent(item.id());
            } catch (Exception markEx) {
                log.error("Outbox message was sent but state update failed: id={}, messageId={}, destination={}",
                        item.id(), item.messageId(), item.destination(), markEx);
                return;
            }
            log.info("Outbox message sent: id={}, messageId={}, destination={}",
                    item.id(), item.messageId(), item.destination());
        } catch (Exception ex) {
            log.warn("Outbox message delivery failed: id={}, messageId={}, destination={}",
                    item.id(), item.messageId(), item.destination(), ex);
            try {
                outboxMessageStateService.handleFailure(item.id(), item.attempts(), ex);
            } catch (Exception stateEx) {
                log.error("Failed to persist outbox failure state: id={}, messageId={}, destination={}",
                        item.id(), item.messageId(), item.destination(), stateEx);
            }
        }
    }
}
