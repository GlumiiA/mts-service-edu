package ru.aigul.mts_service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.aigul.mts_service.messaging.consumer.AsyncTaigaSyncProcessingService;
import ru.aigul.mts_service.messaging.dto.TaigaStoryRequestedMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaigaSyncCommandListener {

    private final AsyncTaigaSyncProcessingService asyncTaigaSyncProcessingService;

    @JmsListener(destination = "${app.messaging.taiga-sync.queue-address}", containerFactory = "jmsListenerContainerFactory")
    public void onTaigaStoryRequested(TaigaStoryRequestedMessage message,
                                       @Header(name = "JMSXDeliveryCount", required = false) Integer deliveryCount) {
        try {
            log.info("JMS Taiga sync message consumed: messageId={}, applicationId={}, deliveryCount={}",
                    message.messageId(), message.applicationId(), deliveryCount);
            asyncTaigaSyncProcessingService.process(message, deliveryCount);
        } catch (Exception ex) {
            log.warn("Taiga sync message processing failed: messageId={}, applicationId={}, deliveryCount={}",
                    message.messageId(), message.applicationId(), deliveryCount, ex);
            throw ex;
        }
    }
}
