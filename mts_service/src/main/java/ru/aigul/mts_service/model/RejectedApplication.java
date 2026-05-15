package ru.aigul.mts_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks applications that were rejected by 1C integration.
 * Marks cases that require manual review.
 */
@Entity
@Table(name = "rejected_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectedApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the rejected application
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /**
     * Reference to sync history that led to rejection
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sync_history_id")
    private OneCyncHistory syncHistory;

    /**
     * Reason for rejection
     */
    @Column(length = 500)
    private String rejectionReason;

    /**
     * Error code from 1C
     */
    @Column(length = 50)
    private String errorCode;

    /**
     * Whether manual review is required
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean manualReviewRequired = true;

    /**
     * When was this reviewed
     */
    private LocalDateTime reviewedAt;

    /**
     * Who reviewed this
     */
    @Column(length = 255)
    private String reviewedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
