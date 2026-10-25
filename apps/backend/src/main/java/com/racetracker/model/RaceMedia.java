package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "race_media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaceMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 20)
    private MediaType mediaType = MediaType.IMAGE;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "storage_scope", columnDefinition = "TEXT")
    private String storageScope;

    @Column(name = "thumbnail_key", columnDefinition = "TEXT")
    private String thumbnailKey;

    private Integer width;

    private Integer height;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MediaStatus status = MediaStatus.PENDING;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum MediaType {
        IMAGE, VIDEO
    }

    public enum MediaStatus {
        PENDING, APPROVED, REJECTED
    }
}
