package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.model.ImportJobSource;
import com.racetracker.repository.ImportJobSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds participant import uploads in the database so every backend instance can process or
 * resume a job. Jobs created before database-backed sources keep their local {@code sourcePath}.
 */
@Service
public class ImportSourceStore {

    private final ImportJobSourceRepository repository;

    public ImportSourceStore(ImportJobSourceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(UUID jobId, byte[] content) {
        repository.save(new ImportJobSource(jobId, content));
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> read(UUID jobId) {
        return repository.findById(jobId).map(ImportJobSource::getContent);
    }

    @Transactional(readOnly = true)
    public boolean hasSource(ImportJob job) {
        return (job.getSourcePath() != null && !job.getSourcePath().isBlank()) || repository.existsById(job.getId());
    }

    @Transactional
    public void delete(UUID jobId) {
        if (repository.existsById(jobId)) {
            repository.deleteById(jobId);
        }
    }
}
