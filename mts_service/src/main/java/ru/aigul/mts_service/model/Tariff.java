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
@Table(name = "tariffs")
@Data
public class Tariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false, length = 150)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TariffCategory category;

    @Column(precision = 10, scale = 2)
    private BigDecimal basePrice;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TariffStatus status = TariffStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Поля для HOME и BUSINESS
    @Column(name = "speed_mbps")
    private Integer speedMbps;

    // Поля для HOME
    @Column(name = "tv_channels")
    private Integer tvChannels;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "tariff_services", joinColumns = @JoinColumn(name = "tariff_id"), inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<Service> services = new HashSet<>();

    // Поля для MOBILE
    @Column(name = "internet_gb")
    private Integer internetGb;

    @Column(name = "calls_minutes")
    private Integer callsMinutes;

    @Column(name = "sms_count")
    private Integer smsCount;

    @Column(name = "for_family", nullable = false)
    private boolean forFamily = false;

    @Column(name = "no_subscription_fee", nullable = false)
    private boolean noSubscriptionFee = false;

    // Поля для DEVICES
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "device_type")
    private DeviceType deviceType;

    // Поля для LANDLINE
    @Column(name = "local_minutes")
    private Integer localMinutes;

    @Column(name = "intercity_minutes")
    private Integer intercityMinutes;

    @Column(name = "mobile_minutes")
    private Integer mobileMinutes;

    // Поля для SATELLITE_TV
    @Column(name = "channels_total")
    private Integer channelsTotal;

    @Column(name = "channels_hd")
    private Integer channelsHd;

    // Поля для BUSINESS
    @Column(name = "sla_description", columnDefinition = "TEXT")
    private String slaDescription;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
