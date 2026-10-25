package com.racetracker.service.impl;

import com.racetracker.model.ImportJob;
import com.racetracker.repository.ImportJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipantImportJobServiceTest {

    @Test
    void exhaustedStaleJobIsFailedWithoutClaimingFourthAttempt() {
        ImportJobRepository repository = mock(ImportJobRepository.class);
        UUID jobId = UUID.randomUUID();
        ImportJob job = new ImportJob();
        job.setSourcePath("/tmp/import.csv");
        job.setStatus(ImportJob.JobStatus.PROCESSING);
        job.setAttempts(3);
        job.setUpdatedAt(OffsetDateTime.now());
        when(repository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        ParticipantImportJobService service = new ParticipantImportJobService(repository);
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);

        assertThat(service.claim(jobId, cutoff)).isEmpty();
        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.PROCESSING);
        verify(repository, never()).saveAndFlush(any(ImportJob.class));

        job.setUpdatedAt(OffsetDateTime.now().minusMinutes(20));
        assertThat(service.claim(jobId, cutoff)).isEmpty();
        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(3);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getSourcePath()).isEqualTo("/tmp/import.csv");
        verify(repository).saveAndFlush(job);
    }

    @Test
    void exhaustedPendingJobIsFailedWithoutClaimingFourthAttempt() {
        ImportJobRepository repository = mock(ImportJobRepository.class);
        UUID jobId = UUID.randomUUID();
        ImportJob job = new ImportJob();
        job.setSourcePath("/tmp/import.csv");
        job.setStatus(ImportJob.JobStatus.PENDING);
        job.setAttempts(3);
        when(repository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        assertThat(new ParticipantImportJobService(repository).claim(jobId,
            OffsetDateTime.now().minusMinutes(10))).isEmpty();

        assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(3);
        assertThat(job.getSourcePath()).isEqualTo("/tmp/import.csv");
        verify(repository).saveAndFlush(job);
    }

    @Test
    void simultaneousWorkersClaimOnceAndOldAttemptCannotFinishOrFail() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenAnswer(inv -> {
            Connection connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            return connection;
        });
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        ImportJobRepository repository = mock(ImportJobRepository.class);
        UUID jobId = UUID.randomUUID();
        ImportJob job = new ImportJob();
        job.setSourcePath("/tmp/import.csv");
        job.setStatus(ImportJob.JobStatus.PENDING);
        job.setAttempts(0);
        job.setProcessedRows(0);
        job.setTotalRows(0);
        ReentrantLock rowLock = new ReentrantLock();
        when(repository.findByIdForUpdate(jobId)).thenAnswer(inv -> {
            rowLock.lock();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    rowLock.unlock();
                }
            });
            return Optional.of(job);
        });
        when(repository.saveAndFlush(any(ImportJob.class))).thenAnswer(inv -> {
            job.setUpdatedAt(OffsetDateTime.now());
            return job;
        });
        ParticipantImportJobService service = ParticipantImportTransactionProxyTest.proxied(
            new ParticipantImportJobService(repository), manager);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ParticipantImportJobService.Claim>> first = workers.submit(() -> {
                start.await();
                return service.claim(jobId, OffsetDateTime.now().minusMinutes(10));
            });
            Future<Optional<ParticipantImportJobService.Claim>> second = workers.submit(() -> {
                start.await();
                return service.claim(jobId, OffsetDateTime.now().minusMinutes(10));
            });
            start.countDown();
            int claimed = (first.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                + (second.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0);
            assertThat(claimed).isEqualTo(1);
            assertThat(job.getAttempts()).isEqualTo(1);
            assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.PROCESSING);

            job.setUpdatedAt(OffsetDateTime.now().minusMinutes(20));
            assertThat(service.claim(jobId, OffsetDateTime.now().minusMinutes(10)))
                .get().extracting(ParticipantImportJobService.Claim::attempt).isEqualTo(2);
            assertThat(service.fail(jobId, 1, new IllegalStateException())).isFalse();
            assertThat(service.complete(jobId, 1)).isFalse();
            assertThat(job.getStatus()).isEqualTo(ImportJob.JobStatus.PROCESSING);
            assertThat(job.getAttempts()).isEqualTo(2);
        } finally {
            workers.shutdownNow();
        }
    }
}
