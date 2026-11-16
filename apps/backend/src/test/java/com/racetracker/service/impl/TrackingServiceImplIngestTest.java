package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.tracking.GpsLocationBatchRequest;
import com.racetracker.dto.tracking.GpsLocationRequest;
import com.racetracker.dto.tracking.LiveTrackingResponse;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.GpsLocation;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.service.CheckpointProgression;
import com.racetracker.service.GpsDriftFilter;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import com.racetracker.websocket.TrackingLivePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingServiceImplIngestTest {

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final GpsLocationRepository gpsLocationRepository = mock(GpsLocationRepository.class);
    private final CheckpointRecordRepository checkpointRecordRepository = mock(CheckpointRecordRepository.class);
    private final TrackingLivePublisher publisher = mock(TrackingLivePublisher.class);
    private final CheckpointRepository checkpointRepository = mock(CheckpointRepository.class);
    private final com.racetracker.repository.SelfScanClaimRepository claimRepository =
        mock(com.racetracker.repository.SelfScanClaimRepository.class);
    private final CheckpointProgression progression = mock(CheckpointProgression.class);
    private TrackingServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Race race;
    private Participant participant;

    @BeforeEach
    void setUp() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getTracking().setAccuracyThresholdMeters(100);
        properties.getTracking().setMaxPlausibleSpeedKmh(60);
        properties.getTracking().setSmoothingFactor(1.0);
        properties.getTracking().setMinDistanceForAccumulationMeters(1);
        properties.getTracking().setCheckpointRadiusMeters(50);
        GeoTelemetryServiceImpl geo = new GeoTelemetryServiceImpl();
        service = new TrackingServiceImpl(raceRepository, participantRepository, userRepository, gpsLocationRepository,
            checkpointRepository, checkpointRecordRepository, publisher, geo,
            new GpsDriftFilter(geo, properties), mock(LiveSummaryCacheInvalidator.class),
            progression, mock(CapabilityAuthorizationService.class), claimRepository, properties);

        race = Race.builder().id(raceId).status(Race.RaceStatus.IN_PROGRESS).build();
        participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .status(Participant.ParticipantStatus.STARTED).totalDistance(BigDecimal.ZERO).build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("runner@example.com", null, List.of()));
        when(raceRepository.existsById(raceId)).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("runner@example.com"))
            .thenReturn(Optional.of(User.builder().id(userId).email("runner@example.com").build()));
        when(participantRepository.findByRaceIdAndUserId(raceId, userId)).thenReturn(Optional.of(participant));
        when(participantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(gpsLocationRepository.save(any(GpsLocation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gpsLocationRepository
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                any(), any(), any()))
            .thenReturn(Optional.empty());
        when(checkpointRecordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any()))
            .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private GpsLocationRequest point(double lat, double lng, OffsetDateTime at) {
        GpsLocationRequest request = new GpsLocationRequest();
        request.setLatitude(BigDecimal.valueOf(lat));
        request.setLongitude(BigDecimal.valueOf(lng));
        request.setAccuracy(BigDecimal.valueOf(5));
        request.setTimestamp(at.toString());
        return request;
    }

    private GpsLocationBatchRequest batch(GpsLocationRequest... points) {
        GpsLocationBatchRequest request = new GpsLocationBatchRequest();
        request.setLocations(List.of(points));
        return request;
    }

    @Test
    void anchorLookupIsBoundedSoAFutureDatedPointCannotFreezeTheAthlete() {
        service.recordLocationsBatch(raceId, batch(point(-17.8300, 31.0500, OffsetDateTime.now().minusSeconds(10))));

        ArgumentCaptor<OffsetDateTime> bound = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(gpsLocationRepository)
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                eq(participant.getId()), eq(raceId), bound.capture());
        assertThat(bound.getValue()).isBefore(OffsetDateTime.now().plusMinutes(5));
        verify(gpsLocationRepository, never())
            .findTopByParticipantIdAndRaceIdAndFilteredFalseOrderByRecordedAtDesc(any(), any());
    }

    @Test
    void futureDatedPointIsStoredAsFilteredAndTheRealPointStillCounts() {
        OffsetDateTime now = OffsetDateTime.now();
        service.recordLocationsBatch(raceId, batch(
            point(-17.8300, 31.0500, now.minusSeconds(20)),
            point(-17.8400, 31.0500, now.plusYears(3)),
            point(-17.8310, 31.0500, now.minusSeconds(5))));

        ArgumentCaptor<GpsLocation> saved = ArgumentCaptor.forClass(GpsLocation.class);
        verify(gpsLocationRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues().get(2).getFilterReason()).isEqualTo("FUTURE_TIMESTAMP");
        assertThat(saved.getAllValues().get(1).getFiltered()).isFalse();
        verify(participantRepository).incrementTotalDistance(eq(participant.getId()), any(BigDecimal.class));
    }

    @Test
    void outOfOrderBatchIsSortedBeforeFiltering() {
        OffsetDateTime now = OffsetDateTime.now();
        service.recordLocationsBatch(raceId, batch(
            point(-17.8310, 31.0500, now.minusSeconds(5)),
            point(-17.8300, 31.0500, now.minusSeconds(20))));

        ArgumentCaptor<GpsLocation> saved = ArgumentCaptor.forClass(GpsLocation.class);
        verify(gpsLocationRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(location -> assertThat(location.getFiltered()).isFalse());
    }

    @Test
    void streamIsLockedBeforeTheAnchorIsReadSoConcurrentUploadsCannotDoubleCount() {
        service.recordLocationsBatch(raceId, batch(point(-17.8300, 31.0500, OffsetDateTime.now().minusSeconds(10))));

        InOrder order = inOrder(gpsLocationRepository);
        order.verify(gpsLocationRepository).lockLocationStream("gps-stream:" + participant.getId());
        order.verify(gpsLocationRepository)
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                any(), any(), any());
    }

    @Test
    void beforeTheRaceStartsPositionIsLiveButDistanceDoesNotAccumulate() {
        race.setStatus(Race.RaceStatus.REGISTRATION_CLOSED);
        participant.setStatus(Participant.ParticipantStatus.CHECKED_IN);
        OffsetDateTime now = OffsetDateTime.now();

        service.recordLocationsBatch(raceId, batch(
            point(-17.8300, 31.0500, now.minusSeconds(20)),
            point(-17.8310, 31.0500, now.minusSeconds(5))));

        verify(participantRepository, never()).incrementTotalDistance(any(), any());
        verify(publisher).publishLocationUpdate(eq(raceId), any(LiveTrackingResponse.class));
    }

    @Test
    void finishedAthleteIsToldTrackingHasEndedAndNothingIsStored() {
        participant.setStatus(Participant.ParticipantStatus.FINISHED);

        assertThatThrownBy(() -> service.recordLocationsBatch(raceId,
            batch(point(-17.83, 31.05, OffsetDateTime.now().minusSeconds(5)))))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("TRACKING_CLOSED");
        verify(gpsLocationRepository, never()).save(any());
    }

    @Test
    void closedRaceStopsTracking() {
        race.setStatus(Race.RaceStatus.COMPLETED);

        assertThatThrownBy(() -> service.recordLocation(raceId, point(-17.83, 31.05, OffsetDateTime.now())))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("TRACKING_CLOSED");
    }

    @Test
    void waitlistedEntrantIsNotTracked() {
        participant.setStatus(Participant.ParticipantStatus.WAITLISTED);

        assertThatThrownBy(() -> service.recordLocation(raceId, point(-17.83, 31.05, OffsetDateTime.now())))
            .isInstanceOf(AccessDeniedException.class);
        verify(gpsLocationRepository, never()).save(any());
    }

    @Test
    void liveUpdateIsPublishedOnlyAfterTheTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        service.recordLocation(raceId, point(-17.83, 31.05, OffsetDateTime.now().minusSeconds(5)));

        verify(publisher, never()).publishLocationUpdate(any(), any());
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<LiveTrackingResponse> update = ArgumentCaptor.forClass(LiveTrackingResponse.class);
        verify(publisher).publishLocationUpdate(eq(raceId), update.capture());
        assertThat(update.getValue().getRecordedAt()).isNotNull();
    }

    @Test
    void staffLiveViewUsesBatchedQueriesAndReportsTheFixTime() {
        Participant other = Participant.builder().id(UUID.randomUUID()).race(race)
            .status(Participant.ParticipantStatus.STARTED).build();
        OffsetDateTime fix = OffsetDateTime.now().minusMinutes(40);
        GpsLocation stale = new GpsLocation();
        stale.setParticipant(participant);
        stale.setLatitude(BigDecimal.valueOf(-17.83));
        stale.setLongitude(BigDecimal.valueOf(31.05));
        stale.setRecordedAt(fix);
        when(participantRepository.findByRaceId(raceId)).thenReturn(List.of(participant, other));
        when(gpsLocationRepository.findLatestByRaceIdAndParticipantIds(eq(raceId), anyList())).thenReturn(List.of(stale));
        when(checkpointRecordRepository.findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(eq(raceId), anyList()))
            .thenReturn(List.of());

        List<LiveTrackingResponse> rows = service.getLiveData(raceId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getRecordedAt()).isEqualTo(fix.toString());
        verify(gpsLocationRepository).findLatestByRaceIdAndParticipantIds(eq(raceId), anyList());
        verify(gpsLocationRepository, never())
            .findTopByParticipantIdAndRaceIdAndFilteredFalseOrderByRecordedAtDesc(any(), any());
        verify(checkpointRecordRepository, never()).findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any());
    }

    private com.racetracker.model.Checkpoint checkpoint(com.racetracker.model.Checkpoint.CheckpointType type) {
        com.racetracker.model.Checkpoint checkpoint = com.racetracker.model.Checkpoint.builder()
            .id(UUID.randomUUID()).race(race).name("Water 2").checkpointType(type)
            .latitude(BigDecimal.valueOf(-17.8300)).longitude(BigDecimal.valueOf(31.0500))
            .selfScanToken("secret-token").build();
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpoint.getId())).thenReturn(Optional.of(checkpoint));
        when(checkpointRecordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()))
            .thenReturn(Optional.empty());
        return checkpoint;
    }

    private com.racetracker.dto.tracking.TrackingCheckpointRequest scan(com.racetracker.model.Checkpoint checkpoint,
                                                                         String token) {
        com.racetracker.dto.tracking.TrackingCheckpointRequest request = new com.racetracker.dto.tracking.TrackingCheckpointRequest();
        request.setCheckpointId(checkpoint.getId());
        request.setScanToken(token);
        return request;
    }

    private void lastFix(double lat, double lng, OffsetDateTime at) {
        GpsLocation fix = new GpsLocation();
        fix.setParticipant(participant);
        fix.setLatitude(BigDecimal.valueOf(lat));
        fix.setLongitude(BigDecimal.valueOf(lng));
        fix.setAccuracy(BigDecimal.valueOf(8));
        fix.setRecordedAt(at);
        when(gpsLocationRepository
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                any(), any(), any()))
            .thenReturn(Optional.of(fix));
    }

    @Test
    void selfScanNearTheCheckpointWithAFreshFixIsVerified() {
        com.racetracker.model.Checkpoint checkpoint = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE);
        lastFix(-17.83020, 31.05000, OffsetDateTime.now().minusSeconds(20));

        com.racetracker.dto.tracking.SelfScanResultDto result = service.recordCheckpoint(raceId, scan(checkpoint, "secret-token"));

        assertThat(result.status()).isEqualTo("VERIFIED");
        verify(checkpointRecordRepository).save(any(com.racetracker.model.CheckpointRecord.class));
        verify(progression).apply(eq(participant), eq(checkpoint), eq(false));
        verify(claimRepository, never()).save(any());
    }

    @Test
    void selfScanFarFromTheCheckpointBecomesAProvisionalClaimAndChangesNothingOfficial() {
        com.racetracker.model.Checkpoint checkpoint = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE);
        lastFix(-17.8500, 31.0500, OffsetDateTime.now().minusSeconds(20));

        com.racetracker.dto.tracking.SelfScanResultDto result = service.recordCheckpoint(raceId, scan(checkpoint, "secret-token"));

        assertThat(result.status()).isEqualTo("PROVISIONAL");
        assertThat(result.reason()).isEqualTo("OUT_OF_RANGE");
        ArgumentCaptor<com.racetracker.model.SelfScanClaim> claim = ArgumentCaptor.forClass(com.racetracker.model.SelfScanClaim.class);
        verify(claimRepository).save(claim.capture());
        assertThat(claim.getValue().getDistanceMeters()).isGreaterThan(BigDecimal.valueOf(2000));
        verify(checkpointRecordRepository, never()).save(any());
        verify(progression, never()).apply(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void selfScanWithAStaleFixIsProvisional() {
        com.racetracker.model.Checkpoint checkpoint = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE);
        lastFix(-17.83000, 31.05000, OffsetDateTime.now().minusMinutes(10));

        assertThat(service.recordCheckpoint(raceId, scan(checkpoint, "secret-token")).reason()).isEqualTo("NO_RECENT_FIX");
        verify(checkpointRecordRepository, never()).save(any());
    }

    @Test
    void selfScanWithTheWrongCodeIsRefused() {
        com.racetracker.model.Checkpoint checkpoint = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE);

        assertThatThrownBy(() -> service.recordCheckpoint(raceId, scan(checkpoint, null)))
            .isInstanceOf(com.racetracker.exception.ApiException.class)
            .extracting("code").isEqualTo("SELF_SCAN_CODE_INVALID");
        assertThatThrownBy(() -> service.recordCheckpoint(raceId, scan(checkpoint, "guessed")))
            .extracting("code").isEqualTo("SELF_SCAN_CODE_INVALID");
        verify(checkpointRecordRepository, never()).save(any());
    }

    @Test
    void finishAndStartNeverAcceptSelfScans() {
        com.racetracker.model.Checkpoint finish = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.FINISH);
        com.racetracker.model.Checkpoint start = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.START);
        lastFix(-17.83000, 31.05000, OffsetDateTime.now().minusSeconds(5));

        assertThatThrownBy(() -> service.recordCheckpoint(raceId, scan(finish, "secret-token")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SELF_SCAN_NOT_ALLOWED");
        assertThatThrownBy(() -> service.recordCheckpoint(raceId, scan(start, "secret-token")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SELF_SCAN_NOT_ALLOWED");
        verify(checkpointRecordRepository, never()).save(any());
    }

    @Test
    void selfScanOutsideAnInProgressRaceIsRefused() {
        race.setStatus(Race.RaceStatus.REGISTRATION_CLOSED);
        com.racetracker.model.Checkpoint checkpoint = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE);

        assertThatThrownBy(() -> service.recordCheckpoint(raceId, scan(checkpoint, "secret-token")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("RACE_NOT_IN_PROGRESS");
    }

    @Test
    void locationSentWithTheScanGoesThroughTheNormalGpsFilters() {
        com.racetracker.model.Checkpoint checkpoint = checkpoint(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE);
        com.racetracker.dto.tracking.TrackingCheckpointRequest request = scan(checkpoint, "secret-token");
        request.setLocation(point(-17.83010, 31.05000, OffsetDateTime.now().plusYears(2)));

        service.recordCheckpoint(raceId, request);

        ArgumentCaptor<GpsLocation> saved = ArgumentCaptor.forClass(GpsLocation.class);
        verify(gpsLocationRepository).save(saved.capture());
        assertThat(saved.getValue().getFilterReason()).isEqualTo("FUTURE_TIMESTAMP");
    }

    @Test
    void athleteTimedOnlyByRfidAppearsOnTheStaffMapAtTheirLastMat() {
        com.racetracker.model.Checkpoint mat = com.racetracker.model.Checkpoint.builder().id(UUID.randomUUID())
            .name("10 km").checkpointType(com.racetracker.model.Checkpoint.CheckpointType.INTERMEDIATE)
            .latitude(BigDecimal.valueOf(-17.80)).longitude(BigDecimal.valueOf(31.04))
            .distanceFromStartKm(BigDecimal.TEN).selfScanToken("t").build();
        com.racetracker.model.CheckpointRecord crossing = new com.racetracker.model.CheckpointRecord();
        crossing.setParticipant(participant);
        crossing.setCheckpoint(mat);
        crossing.setRecordedAt(OffsetDateTime.now().minusMinutes(4));
        when(participantRepository.findByRaceId(raceId)).thenReturn(List.of(participant));
        when(gpsLocationRepository.findLatestByRaceIdAndParticipantIds(eq(raceId), anyList())).thenReturn(List.of());
        when(checkpointRecordRepository.findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(eq(raceId), anyList()))
            .thenReturn(List.of(crossing));

        List<LiveTrackingResponse> rows = service.getLiveData(raceId);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getPositionSource()).isEqualTo("CHECKPOINT");
            assertThat(row.getLatitude()).isEqualByComparingTo("-17.80");
            assertThat(row.getLastCheckpoint()).isEqualTo("10 km");
            assertThat(row.getRecordedAt()).isEqualTo(crossing.getRecordedAt().toString());
        });
    }
}
