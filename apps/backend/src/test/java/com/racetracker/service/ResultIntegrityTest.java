package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.model.Participant;
import com.racetracker.model.RaceResult;
import com.racetracker.model.RaceResultVersion;
import com.racetracker.repository.RaceResultVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultIntegrityTest {

    private final RaceResultVersionRepository repository = mock(RaceResultVersionRepository.class);
    private final ResultVersioning versioning = new ResultVersioning(repository, new ObjectMapper());
    private final UUID raceId = UUID.randomUUID();

    private RaceResult row(UUID participantId, int position, long chipSeconds) {
        Participant participant = new Participant();
        participant.setId(participantId);
        RaceResult result = new RaceResult();
        result.setParticipant(participant);
        result.setOverallPosition(position);
        result.setChipTime(Duration.ofSeconds(chipSeconds));
        result.setGunTime(Duration.ofSeconds(chipSeconds + 12));
        return result;
    }

    @Test
    void publishingChainsEachVersionToThePreviousOneAndSupersedesIt() {
        when(repository.save(any(RaceResultVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        List<RaceResult> first = List.of(row(UUID.randomUUID(), 1, 3600));
        when(repository.findTopByRaceIdOrderByVersionDesc(raceId)).thenReturn(Optional.empty());
        RaceResultVersion v1 = versioning.record(raceId, first, null);

        when(repository.findTopByRaceIdOrderByVersionDesc(raceId)).thenReturn(Optional.of(v1));
        RaceResultVersion v2 = versioning.record(raceId, first, null);

        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v1.getStatus()).isEqualTo(RaceResultVersion.Status.SUPERSEDED);
        assertThat(v2.getStatus()).isEqualTo(RaceResultVersion.Status.PROVISIONAL);
        assertThat(v2.getPreviousChainSha256()).isEqualTo(v1.getChainSha256());
        assertThat(v2.getChainSha256()).isNotEqualTo(v1.getChainSha256());
        assertThat(v2.getContentSha256()).isEqualTo(v1.getContentSha256());
        verify(repository, org.mockito.Mockito.times(2)).save(v1);
    }

    @Test
    void anyChangeToAResultChangesTheHashAndRowOrderDoesNot() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String original = versioning.contentHash(versioning.lines(List.of(row(a, 1, 3600), row(b, 2, 3700))));
        String reordered = versioning.contentHash(versioning.lines(List.of(row(b, 2, 3700), row(a, 1, 3600))));
        String tampered = versioning.contentHash(versioning.lines(List.of(row(a, 1, 3599), row(b, 2, 3700))));

        assertThat(reordered).isEqualTo(original);
        assertThat(tampered).isNotEqualTo(original);
    }

    @Test
    void storedSnapshotHashesBackToTheRecordedContentHashWithoutPersonalData() {
        when(repository.save(any(RaceResultVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findTopByRaceIdOrderByVersionDesc(raceId)).thenReturn(Optional.empty());
        RaceResult result = row(UUID.randomUUID(), 1, 3600);
        result.getParticipant().setFirstName("Tari");
        RaceResultVersion version = versioning.record(raceId, List.of(result), null);

        assertThat(versioning.contentHash(versioning.snapshotLines(version))).isEqualTo(version.getContentSha256());
        assertThat(version.getSnapshot()).doesNotContain("Tari");
        assertThat(ResultVersioning.chainHash(null, version.getContentSha256(), 1, raceId)).isEqualTo(version.getChainSha256());
    }
}
