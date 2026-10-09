package com.racetracker.service;

import com.racetracker.dto.favorite.FavoriteCreateRequest;
import com.racetracker.model.AthleteFollow;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.AthleteFollowServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AthleteFollowServiceTest {

    @Mock AthleteFollowRepository followRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock RaceRepository raceRepository;
    @Mock UserRepository userRepository;
    @Mock LastCheckpointNameCache lastCheckpointNameCache;

    private AthleteFollowServiceImpl service;
    private User user;
    private Race race;
    private Participant participant;
    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();
    private final String email = "fan@example.com";

    @BeforeEach
    void setUp() {
        service = new AthleteFollowServiceImpl(followRepository, participantRepository, raceRepository, userRepository, lastCheckpointNameCache);
        user = User.builder().id(UUID.randomUUID()).email(email).firstName("Fan").lastName("One").build();
        race = new Race();
        race.setId(raceId);
        race.setName("City Marathon");
        race.setDistanceKm(BigDecimal.TEN);
        participant = new Participant();
        participant.setId(participantId);
        participant.setRace(race);
        participant.setFirstName("Ada");
        participant.setLastName("Runner");
        participant.setBibNumber("101");
        participant.setTotalDistance(BigDecimal.valueOf(4));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        lenient().when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void followIsIdempotentWhenAlreadyFollowing() {
        AthleteFollow existing = AthleteFollow.builder().id(UUID.randomUUID()).user(user).race(race).participant(participant).build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(followRepository.findByUserIdAndParticipantId(user.getId(), participantId)).thenReturn(Optional.of(existing));

        FavoriteCreateRequest request = new FavoriteCreateRequest();
        request.setRaceId(raceId);
        request.setParticipantId(participantId);

        var dto = service.follow(request);

        assertThat(dto.getParticipantId()).isEqualTo(participantId);
        verify(followRepository, never()).save(any());
    }

    @Test
    void followCreatesWhenMissing() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant));
        when(followRepository.findByUserIdAndParticipantId(user.getId(), participantId)).thenReturn(Optional.empty());
        when(followRepository.save(any(AthleteFollow.class))).thenAnswer(inv -> inv.getArgument(0));

        FavoriteCreateRequest request = new FavoriteCreateRequest();
        request.setRaceId(raceId);
        request.setParticipantId(participantId);

        var dto = service.follow(request);

        assertThat(dto.getParticipantName()).isEqualTo("Ada Runner");
        assertThat(dto.getBibNumber()).isEqualTo("101");
        verify(followRepository).save(any(AthleteFollow.class));
    }

    @Test
    void listReturnsFollowedAthletes() {
        AthleteFollow follow = AthleteFollow.builder().id(UUID.randomUUID()).user(user).race(race).participant(participant).build();
        when(followRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(follow));

        var favorites = service.listFavorites();

        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).getDistanceRemaining()).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    void unfollowDeletesExistingFollow() {
        AthleteFollow follow = AthleteFollow.builder().id(UUID.randomUUID()).user(user).race(race).participant(participant).build();
        when(followRepository.findByUserIdAndParticipantId(user.getId(), participantId)).thenReturn(Optional.of(follow));

        service.unfollow(raceId, participantId);

        verify(followRepository).delete(follow);
    }
}
