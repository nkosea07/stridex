package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "race_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    private String gender; // 'MALE', 'FEMALE', 'ANY'

    @Column(name = "bib_prefix")
    private String bibPrefix;

    @Column(name = "max_participants")
    private Integer maxParticipants;

    @Column(name = "registration_fee", precision = 10, scale = 2)
    private BigDecimal registrationFee;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "category")
    private Set<Participant> participants = new HashSet<>();
}
