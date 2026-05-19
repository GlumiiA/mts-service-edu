package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.dto.ConnectionRequestedMessage;
import ru.aigul.mts_service.service.AsyncConnectionProcessingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionCommandListener {

    private final AsyncConnectionProcessingService asyncConnectionProcessingService;

    @JmsListener(destination = "${app.messaging.connection.queue-address}", containerFactory = "jmsListenerContainerFactory")
    public void onConnectionRequested(ConnectionRequestedMessage message,
                                      @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
        try {
            log.info("JMS connection message consumed: messageId={}, applicationId={}, correlationId={}",
                    message.messageId(), message.applicationId(), message.correlationId());
            asyncConnectionProcessingService.process(message);
        } catch (Exception ex) {
            log.warn("Connection message processing failed: messageId={}, applicationId={}, deliveryCount={}",
                    message.messageId(), message.applicationId(), deliveryCount, ex);
            throw ex;
        }
    }
}

