package com.racetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "race_result_versions")
@Getter
@Setter
@NoArgsConstructor
public class RaceResultVersion {

    public enum Status { PROVISIONAL, OFFICIAL, SUPERSEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "race_id", nullable = false, updatable = false)
    private UUID raceId;

    @Column(nullable = false, updatable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "result_count", nullable = false, updatable = false)
    private Integer resultCount;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "previous_chain_sha256", updatable = false, length = 64)
    private String previousChainSha256;

    @Column(name = "chain_sha256", nullable = false, updatable = false, length = 64)
    private String chainSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "published_by", updatable = false)
    private UUID publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "certified_by")
    private UUID certifiedBy;

    @Column(name = "certified_at")
    private OffsetDateTime certifiedAt;
}
