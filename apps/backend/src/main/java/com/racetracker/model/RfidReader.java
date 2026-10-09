package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "rfid_readers",
    uniqueConstraints = @UniqueConstraint(columnNames = {"race_id", "external_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidReader {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private Checkpoint checkpoint;

    @Column(nullable = false)
    private String name;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "rfid_reader_status")
    private RfidReaderStatus status = RfidReaderStatus.ACTIVE;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "last_ip")
    private String lastIp;

    @Column(name = "gateway_name")
    private String gatewayName;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "gateway_status", columnDefinition = "rfid_gateway_status")
    private GatewayStatus gatewayStatus = GatewayStatus.OFFLINE;

    @Column(name = "gateway_backlog")
    private Integer gatewayBacklog = 0;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "hardware_model")
    private String hardwareModel;

    @Column(name = "hardware_profile")
    private String hardwareProfile;

    @Column(name = "reader_class")
    private String readerClass;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "last_processed_at")
    private OffsetDateTime lastProcessedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum RfidReaderStatus {
        ACTIVE, REVOKED
    }

    public enum GatewayStatus {
        ONLINE, DEGRADED, OFFLINE
    }
}
