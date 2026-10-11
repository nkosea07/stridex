package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "user_role")
    private UserRole role = UserRole.ATHLETE;

    private String phone;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "failed_login_attempts")
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_failed_login_at")
    private OffsetDateTime lastFailedLoginAt;

    @Column(name = "lockout_until")
    private OffsetDateTime lockoutUntil;

    @Column(name = "data_processing_consent_at")
    private OffsetDateTime dataProcessingConsentAt;

    @Column(name = "marketing_consent")
    private Boolean marketingConsent = false;

    @Column(name = "privacy_policy_version", length = 40)
    private String privacyPolicyVersion;

    @Column(name = "privacy_policy_accepted_at")
    private OffsetDateTime privacyPolicyAcceptedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "createdBy")
    private Set<Race> createdRaces = new HashSet<>();

    @ManyToMany(mappedBy = "admins")
    private Set<Race> adminRaces = new HashSet<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public enum UserRole {
        SUPER_ADMIN, RACE_ADMIN, OFFICIAL, VOLUNTEER, ATHLETE, SPECTATOR
    }
}
