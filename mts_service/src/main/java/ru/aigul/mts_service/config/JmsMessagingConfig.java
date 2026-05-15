package ru.aigul.mts_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.jms.BytesMessage;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableJms
public class JmsMessagingConfig {

    @Bean
    public ConnectionFactory jmsConnectionFactory(
            @Value("${app.messaging.broker-url}") String brokerUrl,
            @Value("${app.messaging.username}") String username,
            @Value("${app.messaging.password}") String password) {
        return new JmsConnectionFactory(username, password, brokerUrl);
    }

    @Bean
    public MessageConverter jmsMessageConverter(ObjectMapper objectMapper) {
        return new MessageConverter() {
            @Override
            public Message toMessage(Object object, Session session) throws JMSException {
                TextMessage message = session.createTextMessage();
                try {
                    message.setText(objectMapper.writeValueAsString(object));
                    message.setStringProperty("_type", object.getClass().getName());
                    return message;
                } catch (Exception ex) {
                    throw new JMSException("Failed to serialize JMS payload: " + ex.getMessage());
                }
            }

            @Override
            public Object fromMessage(Message message) throws JMSException {
                try {
                    String payload;
                    if (message instanceof TextMessage textMessage) {
                        payload = textMessage.getText();
                    } else if (message instanceof BytesMessage bytesMessage) {
                        byte[] data = new byte[(int) bytesMessage.getBodyLength()];
                        bytesMessage.readBytes(data);
                        payload = new String(data, StandardCharsets.UTF_8);
                    } else {
                        throw new JMSException("Unsupported JMS message type: " + message.getClass().getName());
                    }

                    String typeId = message.getStringProperty("_type");
                    if (typeId == null || typeId.isBlank()) {
                        throw new JMSException("Missing JMS type header '_type'");
                    }

                    Class<?> payloadType = Class.forName(typeId);
                    return objectMapper.readValue(payload, payloadType);
                } catch (JMSException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new JMSException("Failed to deserialize JMS payload: " + ex.getMessage());
                }
            }
        };
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory, MessageConverter jmsMessageConverter) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(jmsMessageConverter);
        template.setPubSubDomain(false);
        return template;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jmsMessageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jmsMessageConverter);
        factory.setSessionTransacted(true);
        factory.setConcurrency("1-4");
        return factory;
    }
}
