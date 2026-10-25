package com.racetracker.service;

import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OperationsCheckpointServiceTest {
    @Test
    void scopesPageToAuthenticatedVolunteer() {
        CheckpointRepository repository = mock(CheckpointRepository.class);
        new OperationsCheckpointService(repository).list("volunteer@example.com", 2, 50);
        verify(repository).findOperationsForVolunteer("volunteer@example.com", User.UserRole.VOLUNTEER,
            PageRequest.of(2, 50));
    }
}
