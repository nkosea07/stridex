package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "checkpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Checkpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "checkpoint_type", nullable = false, columnDefinition = "checkpoint_type")
    private CheckpointType checkpointType;

    @Column(name = "distance_from_start_km", precision = 6, scale = 2)
    private BigDecimal distanceFromStartKm;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "cutoff_time")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration cutoffTime;

    @Column(name = "has_rfid_reader")
    private Boolean hasRfidReader = false;

    @Column(name = "has_qr_scanner")
    private Boolean hasQrScanner = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "checkpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CheckpointRecord> records = new HashSet<>();

    @ManyToMany
    @JoinTable(
        name = "checkpoint_volunteers",
        joinColumns = @JoinColumn(name = "checkpoint_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> volunteers = new HashSet<>();

    public enum CheckpointType {
        START, INTERMEDIATE, WATER_STATION, MEDICAL, FINISH
    }
}
