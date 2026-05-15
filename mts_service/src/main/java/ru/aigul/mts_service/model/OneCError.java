package ru.aigul.mts_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Stores detailed error information from 1C integration attempts.
 */
@Entity
@Table(name = "one_c_errors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneCError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to sync history record
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_history_id", nullable = false)
    private OneCyncHistory syncHistory;

    /**
     * Error code from 1C system
     */
    @Column(length = 50)
    private String errorCode;

    /**
     * Human-readable error message
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Classified error type
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ErrorDetailEnum errorDetail;

    /**
     * Full stack trace if available
     */
    @Column(columnDefinition = "TEXT")
    private String errorStacktrace;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
