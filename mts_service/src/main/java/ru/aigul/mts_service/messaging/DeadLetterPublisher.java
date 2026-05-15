package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.dto.DeadLetterMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterPublisher {

    private final JmsTemplate jmsTemplate;

    @Value("${app.messaging.dead-letter.queue-address}")
    private String deadLetterQueueAddress;

    public void publish(DeadLetterMessage message) {
        jmsTemplate.convertAndSend(deadLetterQueueAddress, message);
        log.error("Moved message to DLQ: messageId={}, eventType={}, destination={}",
                message.messageId(), message.eventType(), deadLetterQueueAddress);
    }
}

