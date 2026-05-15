package ru.aigul.mts_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_messages")
@Getter
@Setter
public class ProcessedMessage {

    @Id
    @Column(length = 36)
    private String messageId;

    @Column(nullable = false, length = 80)
    private String consumer;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime processedAt;
}

