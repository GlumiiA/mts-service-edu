package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;
import ru.aigul.mts_service.service.AsyncConnectionProcessingService;
import ru.aigul.mts_service.service.ListenerFailureHandler;
import ru.aigul.mts_service.service.MessageIdempotencyService;
import ru.aigul.mts_service.service.OutboxService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionCommandListener {

    private final AsyncConnectionProcessingService asyncConnectionProcessingService;
    private final MessageIdempotencyService messageIdempotencyService;
    private final ListenerFailureHandler listenerFailureHandler;

    @JmsListener(destination = "${app.messaging.connection.queue-address}", containerFactory = "jmsListenerContainerFactory")
    public void onConnectionRequested(ConnectionRequestedMessage message,
                                      @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
        String messageId = message.messageId();
        if (!messageIdempotencyService.tryRegister(messageId, "connection-listener")) {
            log.info("Duplicate connection message skipped: messageId={}, applicationId={}",
                    messageId, message.applicationId());
            return;
        }

        try {
            log.info("JMS connection message consumed: messageId={}, applicationId={}, correlationId={}",
                    messageId, message.applicationId(), message.correlationId());
            asyncConnectionProcessingService.process(message);
        } catch (Exception ex) {
            boolean shouldRetry = listenerFailureHandler.shouldRetryOrMoveToDlq(
                    OutboxService.EVENT_CONNECTION_REQUESTED,
                    messageId,
                    message,
                    deliveryCount,
                    ex
            );
            if (shouldRetry) {
                throw ex;
            }
        }
    }
}

