package com.racetracker.service.impl;

import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.dto.rfid.RfidReaderDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.RfidApiKeyService;
import com.racetracker.service.RfidReaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RfidReaderServiceImpl implements RfidReaderService {

    private final RfidReaderRepository rfidReaderRepository;
    private final RaceRepository raceRepository;
    private final CheckpointRepository checkpointRepository;
    private final RfidApiKeyService rfidApiKeyService;

    public RfidReaderServiceImpl(
        RfidReaderRepository rfidReaderRepository,
        RaceRepository raceRepository,
        CheckpointRepository checkpointRepository,
        RfidApiKeyService rfidApiKeyService
    ) {
        this.rfidReaderRepository = rfidReaderRepository;
        this.raceRepository = raceRepository;
        this.checkpointRepository = checkpointRepository;
        this.rfidApiKeyService = rfidApiKeyService;
    }

    @Override
    @Transactional
    public RfidReaderCreateResponse create(UUID raceId, RfidReaderCreateRequest request) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Checkpoint checkpoint = checkpointRepository.findByRaceIdAndId(raceId, request.getCheckpointId())
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));

        if (!Boolean.TRUE.equals(checkpoint.getHasRfidReader())) {
            throw new ApiException("Checkpoint is not configured for RFID reads");
        }
        String externalId = request.getExternalId().trim();
        if (rfidReaderRepository.existsByRaceIdAndExternalId(raceId, externalId)) {
            throw new ConflictException("RFID reader externalId already exists for this race");
        }

        String apiKey = rfidApiKeyService.generateKey();
        RfidReader reader = new RfidReader();
        reader.setRace(race);
        reader.setCheckpoint(checkpoint);
        reader.setName(request.getName().trim());
        reader.setExternalId(externalId);
        reader.setApiKeyHash(rfidApiKeyService.hashKey(apiKey));
        reader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);

        RfidReader saved = rfidReaderRepository.save(reader);
        RfidReaderCreateResponse response = new RfidReaderCreateResponse();
        copyReader(saved, response);
        response.setApiKey(apiKey);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfidReaderDto> list(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
        return rfidReaderRepository.findByRaceIdOrderByCreatedAtDesc(raceId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public RfidReaderDto revoke(UUID raceId, UUID readerId) {
        RfidReader reader = rfidReaderRepository.findByRaceIdAndId(raceId, readerId)
            .orElseThrow(() -> new ResourceNotFoundException("RFID reader not found"));
        reader.setStatus(RfidReader.RfidReaderStatus.REVOKED);
        return toDto(rfidReaderRepository.save(reader));
    }

    private RfidReaderDto toDto(RfidReader reader) {
        RfidReaderDto dto = new RfidReaderDto();
        copyReader(reader, dto);
        return dto;
    }

    private void copyReader(RfidReader reader, RfidReaderDto dto) {
        dto.setId(reader.getId());
        dto.setRaceId(reader.getRace() != null ? reader.getRace().getId() : null);
        dto.setCheckpointId(reader.getCheckpoint() != null ? reader.getCheckpoint().getId() : null);
        dto.setName(reader.getName());
        dto.setExternalId(reader.getExternalId());
        dto.setStatus(reader.getStatus() != null ? reader.getStatus().name() : null);
        dto.setLastSeenAt(reader.getLastSeenAt() != null ? reader.getLastSeenAt().toString() : null);
        dto.setLastIp(reader.getLastIp());
        dto.setCreatedAt(reader.getCreatedAt() != null ? reader.getCreatedAt().toString() : null);
        dto.setUpdatedAt(reader.getUpdatedAt() != null ? reader.getUpdatedAt().toString() : null);
    }
}
