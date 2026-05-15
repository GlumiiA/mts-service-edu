package ru.aigul.mts_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aigul.mts_service.model.ProcessedMessage;
import ru.aigul.mts_service.repository.ProcessedMessageRepository;

@Service
@RequiredArgsConstructor
public class MessageIdempotencyService {

    private final ProcessedMessageRepository processedMessageRepository;

    @Transactional
    public boolean tryRegister(String messageId, String consumer) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }

        ProcessedMessage processedMessage = new ProcessedMessage();
        processedMessage.setMessageId(messageId);
        processedMessage.setConsumer(consumer);

        try {
            processedMessageRepository.save(processedMessage);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}

