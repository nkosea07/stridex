package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "races")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "race_type", nullable = false, columnDefinition = "race_type")
    private RaceType raceType;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "race_status")
    private RaceStatus status = RaceStatus.DRAFT;

    // Location
    private String city;
    private String country;

    @Column(name = "start_location_lat", precision = 10, scale = 8)
    private BigDecimal startLocationLat;

    @Column(name = "start_location_lng", precision = 11, scale = 8)
    private BigDecimal startLocationLng;

    @Column(name = "finish_location_lat", precision = 10, scale = 8)
    private BigDecimal finishLocationLat;

    @Column(name = "finish_location_lng", precision = 11, scale = 8)
    private BigDecimal finishLocationLng;

    // Timing
    @Column(name = "race_date", nullable = false)
    private LocalDate raceDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "registration_opens")
    private OffsetDateTime registrationOpens;

    @Column(name = "registration_closes")
    private OffsetDateTime registrationCloses;

    // Details
    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "registration_fee", precision = 10, scale = 2)
    private BigDecimal registrationFee;

    @Column(length = 3)
    private String currency = "USD";

    // Media
    @Column(name = "banner_image_url")
    private String bannerImageUrl;

    @Column(name = "logo_url")
    private String logoUrl;

    // Settings
    @Column(name = "is_public_registration")
    private Boolean isPublicRegistration = true;

    @Column(name = "require_medical_clearance")
    private Boolean requireMedicalClearance = false;

    // Ownership
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RaceCategory> categories = new HashSet<>();

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Checkpoint> checkpoints = new HashSet<>();

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Participant> participants = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "race_admins",
        joinColumns = @JoinColumn(name = "race_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> admins = new HashSet<>();

    public enum RaceType {
        MARATHON, HALF_MARATHON, ULTRA_MARATHON, TEN_K, FIVE_K, TRAIL, CYCLING, TRIATHLON, OTHER
    }

    public enum RaceStatus {
        DRAFT, REGISTRATION_OPEN, REGISTRATION_CLOSED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
