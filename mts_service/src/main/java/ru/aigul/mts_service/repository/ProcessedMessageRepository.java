package ru.aigul.mts_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.aigul.mts_service.model.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}

