package com.racetracker.service.impl;

import com.racetracker.exception.ConflictException;
import com.racetracker.model.ImportJob;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantCredential;
import com.racetracker.model.Race;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantCredentialRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.service.ParticipantCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipantImportTransactionProxyTest {

    @Test
    void credentialConflictRollsBackRowBeforeSkipCommitsInNewTransaction() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        List<Connection> connections = new ArrayList<>();
        when(dataSource.getConnection()).thenAnswer(inv -> {
            Connection connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            connections.add(connection);
            return connection;
        });
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);

        ImportJobRepository jobs = mock(ImportJobRepository.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        ParticipantCredentialRepository credentials = mock(ParticipantCredentialRepository.class);
        ParticipantIdentityService identity = mock(ParticipantIdentityService.class);
        UUID jobId = UUID.randomUUID();
        Race race = new Race();
        race.setId(UUID.randomUUID());
        ImportJob job = new ImportJob();
        job.setStatus(ImportJob.JobStatus.PROCESSING);
        job.setAttempts(1);
        job.setProcessedRows(0);
        job.setSuccessCount(0);
        job.setErrorCount(0);
        when(jobs.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setEmail("runner@example.com");
        participant.setRfidTag("TAG-1");
        when(participants.save(participant)).thenReturn(participant);
        when(credentials.findByParticipantIdAndCredentialTypeOrderByAssignedAtAsc(
            participant.getId(), ParticipantCredential.CredentialType.RFID_UHF_TIMING)).thenReturn(List.of());
        when(credentials.existsByRaceIdAndCredentialTypeAndCredentialValueAndParticipantIdNot(
            race.getId(), ParticipantCredential.CredentialType.RFID_UHF_TIMING, "TAG-1", participant.getId()))
            .thenReturn(true);

        ParticipantCredentialService credentialService = proxied(new ParticipantCredentialServiceImpl(
            credentials, participants), manager);
        ParticipantImportRowService rows = proxied(new ParticipantImportRowService(
            jobs, participants, credentialService, identity), manager);

        assertThatThrownBy(() -> rows.importRow(jobId, 1, 1, participant))
            .isInstanceOf(ConflictException.class);
        assertThat(job.getProcessedRows()).isZero();
        rows.skipRow(jobId, 1, 1);

        assertThat(job.getProcessedRows()).isEqualTo(1);
        assertThat(job.getErrorCount()).isEqualTo(1);
        assertThat(connections).hasSize(2);
        verify(connections.get(0)).rollback();
        verify(connections.get(1)).commit();
        verify(jobs).save(job);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxied(T target, DataSourceTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
