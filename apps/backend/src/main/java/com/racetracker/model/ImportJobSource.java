package com.racetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_job_sources")
@Getter
@Setter
@NoArgsConstructor
public class ImportJobSource {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ImportJobSource(UUID jobId, byte[] content) {
        this.jobId = jobId;
        this.content = content;
        this.createdAt = OffsetDateTime.now();
    }
}
