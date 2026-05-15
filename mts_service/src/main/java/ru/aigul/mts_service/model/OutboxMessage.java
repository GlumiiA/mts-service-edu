package ru.aigul.mts_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_messages")
@Getter
@Setter
public class OutboxMessage {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 120)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String destination;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxMessageStatus status = OutboxMessageStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    @Column
    private OffsetDateTime sentAt;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}

