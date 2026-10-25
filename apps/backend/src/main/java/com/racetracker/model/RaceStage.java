package com.racetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "race_stages")
@Getter
@Setter
@NoArgsConstructor
public class RaceStage {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "stage_date")
    private LocalDate stageDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "route_description", columnDefinition = "TEXT")
    private String routeDescription;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
