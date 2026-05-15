package ru.aigul.mts_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Tracks synchronization/integration history with 1C system.
 * Stores information about:
 * - Applications sent to 1C
 * - Status changes
 * - Retry attempts
 * - External 1C identifiers
 */
@Entity
@Table(name = "one_c_sync_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCyncHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of entity being synced (APPLICATION)
     */
    @Column(nullable = false, length = 50)
    private String entityType;

    /**
     * Reference to the application being synced
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private Application application;

    /**
     * External identifier from 1C system
     */
    @Column(length = 100, unique = true)
    private String externalId;

    /**
     * Current synchronization status
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private OneCIntegrationStatus syncStatus = OneCIntegrationStatus.PENDING;

    /**
     * Direction of sync: TO_1C or FROM_1C
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private SyncDirection syncDirection;

    /**
     * Current retry count
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Maximum number of retries
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxRetries = 5;

    /**
     * Last error message if sync failed
     */
    @Column(columnDefinition = "TEXT")
    private String lastError;

    /**
     * Timestamp of last successful sync
     */
    private LocalDateTime lastSyncAt;

    /**
     * When to retry next (calculated based on exponential backoff)
     */
    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
