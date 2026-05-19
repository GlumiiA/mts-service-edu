package ru.aigul.mts_service.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.aigul.mts_service.model.Application;
import ru.aigul.mts_service.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.aigul.mts_service.model.ApplicationStatus;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    @Query("SELECT a FROM Application a JOIN FETCH a.user JOIN FETCH a.tariff WHERE a.user = :user ORDER BY a.createdAt DESC")
    List<Application> findAllByUserOrderByCreatedAtDesc(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Application a LEFT JOIN FETCH a.user LEFT JOIN FETCH a.tariff WHERE a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT a FROM Application a
            JOIN FETCH a.user JOIN FETCH a.tariff
            WHERE (:userId IS NULL OR a.user.id = :userId)
            AND a.status = COALESCE(:status, a.status)
            AND (:after IS NULL OR a.id > :after)
            ORDER BY a.id ASC
            """)
    List<Application> findAllFiltered(@Param("userId") Long userId,
                                      @Param("status") ApplicationStatus status,
                                      @Param("after") Long after,
                                      Limit limit);
}
