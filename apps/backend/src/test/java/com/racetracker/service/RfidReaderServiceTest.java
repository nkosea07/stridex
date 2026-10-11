package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.dto.rfid.RfidReaderHeartbeatRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReadEventRepository;
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

    @Mock
    private RfidReadEventRepository rfidReadEventRepository;

    private RaceTrackerProperties raceTrackerProperties;
    private RfidReaderServiceImpl service;

    @BeforeEach
    void setUp() {
        raceTrackerProperties = new RaceTrackerProperties();
        service = new RfidReaderServiceImpl(
            rfidReaderRepository,
            raceRepository,
            checkpointRepository,
            rfidReadEventRepository,
            rfidApiKeyService,
            raceTrackerProperties
        );
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

    @Test
    void heartbeatUpdatesGatewayMetadata() {
        RfidReader reader = new RfidReader();
        reader.setId(UUID.randomUUID());
        reader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);

        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.of(reader));
        when(rfidReaderRepository.save(any(RfidReader.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RfidReaderHeartbeatRequest request = new RfidReaderHeartbeatRequest();
        request.setGatewayName("edge-1");
        request.setGatewayBacklog(25);
        request.setFirmwareVersion("1.2.3");
        request.setHardwareModel("Impinj R700");
        request.setHardwareProfile("IMPINJ_EVENT_GRADE");
        request.setReaderClass("FIXED_4_PORT");

        var response = service.heartbeat("api-key", request, "10.0.0.10");

        assertThat(response.getGatewayName()).isEqualTo("edge-1");
        assertThat(response.getGatewayStatus()).isEqualTo("ONLINE");
        assertThat(response.getGatewayBacklog()).isEqualTo(25);
        assertThat(response.getHardwareProfile()).isEqualTo("IMPINJ_EVENT_GRADE");
        assertThat(response.getReaderClass()).isEqualTo("FIXED_4_PORT");
        assertThat(response.getLastIp()).isEqualTo("10.0.0.10");
    }

    @Test
    void heartbeatRejectsUnknownApiKey() {
        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.heartbeat("api-key", new RfidReaderHeartbeatRequest(), "127.0.0.1"));
    }

    @Test
    void overviewReturnsDerivedMetrics() {
        UUID raceId = UUID.randomUUID();
        when(raceRepository.existsById(raceId)).thenReturn(true);

        RfidReader onlineReader = new RfidReader();
        onlineReader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);
        onlineReader.setLastHeartbeatAt(java.time.OffsetDateTime.now());
        onlineReader.setGatewayStatus(RfidReader.GatewayStatus.ONLINE);

        RfidReader degradedReader = new RfidReader();
        degradedReader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);
        degradedReader.setLastHeartbeatAt(java.time.OffsetDateTime.now());
        degradedReader.setGatewayStatus(RfidReader.GatewayStatus.DEGRADED);

        when(rfidReaderRepository.findByRaceIdOrderByCreatedAtDesc(raceId)).thenReturn(java.util.List.of(onlineReader, degradedReader));
        when(rfidReadEventRepository.countByRaceId(raceId)).thenReturn(10L);
        when(rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.RECEIVED)).thenReturn(2L);
        when(rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.DUPLICATE)).thenReturn(3L);
        when(rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG)).thenReturn(1L);
        when(rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.INVALID_READER)).thenReturn(0L);
        when(rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.ERROR)).thenReturn(1L);
        when(rfidReadEventRepository.findAverageIngressLagMillis(raceId)).thenReturn(850L);
        when(rfidReadEventRepository.findOldestReceivedAtByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.RECEIVED))
            .thenReturn(java.time.OffsetDateTime.now().minusSeconds(12));

        var overview = service.overview(raceId);

        assertThat(overview.getTotalReaders()).isEqualTo(2);
        assertThat(overview.getOnlineReaders()).isEqualTo(1);
        assertThat(overview.getDegradedReaders()).isEqualTo(1);
        assertThat(overview.getProcessedEvents()).isEqualTo(8);
        assertThat(overview.getDuplicateRate()).isEqualTo(37.5);
        assertThat(overview.getAverageIngressLagMillis()).isEqualTo(850L);
        assertThat(overview.getOldestQueuedReadAgeSeconds()).isGreaterThanOrEqualTo(12L);
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
