package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(nullable = false)
    private String name;

    @Column(name = "club_name")
    private String clubName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_user_id")
    private User captainUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamStatus status = TeamStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum TeamStatus {
        OPEN, LOCKED
    }
}
