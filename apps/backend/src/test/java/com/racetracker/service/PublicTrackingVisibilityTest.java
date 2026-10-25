package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceFeedRevisionRepository;
import com.racetracker.service.impl.PublicTrackingServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicTrackingVisibilityTest {

    @Test
    void hiddenRaceStopsEveryPublicTrackingRead() {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        PublicRaceVisibility visibility = mock(PublicRaceVisibility.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        GpsLocationRepository gps = mock(GpsLocationRepository.class);
        CheckpointRecordRepository checkpoints = mock(CheckpointRecordRepository.class);
        RaceFeedRevisionRepository revisions = mock(RaceFeedRevisionRepository.class);
        PublicTrackingServiceImpl service = new PublicTrackingServiceImpl(
            visibility, participants, gps, checkpoints, revisions, mock(LastCheckpointNameCache.class),
            new RaceTrackerProperties());
        doThrow(new ResourceNotFoundException("Race not found")).when(visibility).requireVisible(raceId);

        assertThatThrownBy(() -> service.getRaceHub(raceId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getLiveAthletes(raceId, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getLiveFeed(raceId, null, null, 25)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getAthleteTracking(raceId, participantId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getAthleteTrack(raceId, participantId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getRaceHeatmap(raceId, null, null, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(participants, gps, checkpoints, revisions);
    }
}
