package com.racetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "race_result_revisions")
public class RaceResultRevision {
    @Id
    @Column(name = "race_id")
    private UUID raceId;

    @Column(nullable = false)
    private long revision;

    protected RaceResultRevision() { }
}
