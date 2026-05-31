package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private RaceCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // Personal Info
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "gender_type")
    private Gender gender;

    private String nationality;

    // Race Info
    @Column(name = "bib_number", unique = true)
    private String bibNumber;

    @Column(name = "rfid_tag", unique = true)
    private String rfidTag;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "participant_status")
    private ParticipantStatus status = ParticipantStatus.REGISTERED;

    // Emergency Contact
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relation")
    private String emergencyContactRelation;

    // Medical
    @Column(name = "blood_type")
    private String bloodType;

    @Column(name = "medical_conditions", columnDefinition = "TEXT")
    private String medicalConditions;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    // Club/Team
    @Column(name = "club_name")
    private String clubName;

    @Column(name = "team_name")
    private String teamName;

    // Registration
    @Column(name = "registration_source")
    private String registrationSource;

    @Column(name = "registered_at")
    private OffsetDateTime registeredAt;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;

    // Tracking
    @Column(name = "is_tracking_enabled")
    private Boolean isTrackingEnabled = true;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(name = "total_distance", precision = 10, scale = 3)
    private java.math.BigDecimal totalDistance = java.math.BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<GpsLocation> gpsLocations = new HashSet<>();

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CheckpointRecord> checkpointRecords = new HashSet<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public enum Gender {
        MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY
    }

    public enum ParticipantStatus {
        REGISTERED, CONFIRMED, CHECKED_IN, STARTED, DNF, DNS, DISQUALIFIED, FINISHED
    }
}
