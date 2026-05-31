package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "gps_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(precision = 8, scale = 2)
    private BigDecimal altitude;

    @Column(precision = 6, scale = 2)
    private BigDecimal accuracy;

    @Column(precision = 6, scale = 2)
    private BigDecimal speed; // km/h

    @Column(precision = 5, scale = 2)
    private BigDecimal heading; // degrees

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;
}
