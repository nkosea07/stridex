package com.racetracker.service;

import com.racetracker.dto.checkpoint.OperationCheckpointDto;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsCheckpointService {
    private final CheckpointRepository checkpointRepository;

    public OperationsCheckpointService(CheckpointRepository checkpointRepository) {
        this.checkpointRepository = checkpointRepository;
    }

    @Transactional(readOnly = true)
    public Page<OperationCheckpointDto> list(String actorEmail, int page, int size) {
        return checkpointRepository.findOperationsForVolunteer(actorEmail, User.UserRole.VOLUNTEER,
            PageRequest.of(page, size));
    }
}
