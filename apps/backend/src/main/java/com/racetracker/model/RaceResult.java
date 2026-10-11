package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "race_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private RaceCategory category;

    @Column(name = "gun_time")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration gunTime;

    @Column(name = "chip_time")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration chipTime;

    @Column(name = "overall_position")
    private Integer overallPosition;

    @Column(name = "gender_position")
    private Integer genderPosition;

    @Column(name = "category_position")
    private Integer categoryPosition;

    @Column(name = "age_group")
    private String ageGroup;

    @Column(name = "age_group_position")
    private Integer ageGroupPosition;

    @Column(name = "penalty_seconds")
    private Integer penaltySeconds;

    @Column(name = "average_pace")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration averagePace;

    @Column(name = "average_speed", precision = 5, scale = 2)
    private BigDecimal averageSpeed;

    @Column(name = "is_official")
    private Boolean isOfficial = false;

    @Column(name = "is_disqualified")
    private Boolean isDisqualified = false;

    @Column(name = "disqualification_reason", columnDefinition = "TEXT")
    private String disqualificationReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
