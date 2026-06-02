package com.racetracker.service;

import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.impl.RfidReaderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfidReaderServiceTest {

    @Mock
    private RfidReaderRepository rfidReaderRepository;

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private CheckpointRepository checkpointRepository;

    @Mock
    private RfidApiKeyService rfidApiKeyService;

    private RfidReaderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RfidReaderServiceImpl(rfidReaderRepository, raceRepository, checkpointRepository, rfidApiKeyService);
    }

    @Test
    void createReturnsPlaintextApiKeyOnceAndStoresHash() {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        Race race = race(raceId);
        Checkpoint checkpoint = checkpoint(checkpointId, race, true);
        RfidReaderCreateRequest request = createRequest(checkpointId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpointId)).thenReturn(Optional.of(checkpoint));
        when(rfidReaderRepository.existsByRaceIdAndExternalId(raceId, "reader-1")).thenReturn(false);
        when(rfidApiKeyService.generateKey()).thenReturn("rfid_plaintext");
        when(rfidApiKeyService.hashKey("rfid_plaintext")).thenReturn("hashed");
        when(rfidReaderRepository.save(any(RfidReader.class))).thenAnswer(invocation -> {
            RfidReader reader = invocation.getArgument(0);
            reader.setId(UUID.randomUUID());
            return reader;
        });

        RfidReaderCreateResponse response = service.create(raceId, request);

        assertThat(response.getApiKey()).isEqualTo("rfid_plaintext");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getExternalId()).isEqualTo("reader-1");
    }

    @Test
    void createRejectsCheckpointWithoutRfidReader() {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        Race race = race(raceId);
        Checkpoint checkpoint = checkpoint(checkpointId, race, false);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpointId)).thenReturn(Optional.of(checkpoint));

        assertThrows(ApiException.class, () -> service.create(raceId, createRequest(checkpointId)));
    }

    @Test
    void createRejectsCheckpointOutsideRace() {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race(raceId)));
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpointId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(raceId, createRequest(checkpointId)));
    }

    private RfidReaderCreateRequest createRequest(UUID checkpointId) {
        RfidReaderCreateRequest request = new RfidReaderCreateRequest();
        request.setCheckpointId(checkpointId);
        request.setName("Timing Mat 1");
        request.setExternalId("reader-1");
        return request;
    }

    private Race race(UUID id) {
        Race race = new Race();
        race.setId(id);
        return race;
    }

    private Checkpoint checkpoint(UUID id, Race race, boolean hasRfidReader) {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(id);
        checkpoint.setRace(race);
        checkpoint.setHasRfidReader(hasRfidReader);
        return checkpoint;
    }
}
