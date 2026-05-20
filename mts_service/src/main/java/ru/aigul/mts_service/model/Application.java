package ru.aigul.mts_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "applications")
@Data
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id", nullable = false)
    private Tariff tariff;

    @NotNull
    @Column(nullable = false, length = 500)
    private String address;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectReason;

    @Column(nullable = false)
    private Boolean passportVerified = false;

    @Column(nullable = false)
    private Boolean technicalFeasibility = false;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lockedPrice;

    @Column(name = "taiga_task_id")
    private Long taigaTaskId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "application_services", joinColumns = @JoinColumn(name = "application_id"), inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<Service> additionalServices = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
