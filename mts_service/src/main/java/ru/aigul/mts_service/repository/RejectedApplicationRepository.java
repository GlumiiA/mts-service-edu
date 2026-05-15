package ru.aigul.mts_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.RejectedApplication;

import java.time.LocalDateTime;
import java.util.List;

public interface RejectedApplicationRepository extends JpaRepository<RejectedApplication, Long> {

    /**
     * Find rejected applications requiring manual review
     */
    Page<RejectedApplication> findByManualReviewRequiredTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Count applications requiring review
     */
    Long countByManualReviewRequiredTrue();

    /**
     * Find rejections by application ID
     */
    List<RejectedApplication> findByApplicationId(Long applicationId);

    /**
     * Find rejections by error code (to identify patterns)
     */
    List<RejectedApplication> findByErrorCode(String errorCode);

    /**
     * Find unreviewed rejections in specific time period
     */
    @Query("SELECT r FROM RejectedApplication r WHERE r.manualReviewRequired = true AND r.createdAt >= :startTime ORDER BY r.createdAt DESC")
    List<RejectedApplication> findUnreviewedInPeriod(@Param("startTime") LocalDateTime startTime);

    /**
     * Find rejections reviewed by specific user
     */
    List<RejectedApplication> findByReviewedByOrderByReviewedAtDesc(String reviewedBy);
}
