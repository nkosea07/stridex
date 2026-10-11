package com.racetracker.service;

import com.racetracker.dto.race.RaceCloneRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.mapper.RaceMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.repository.RaceRegistrationFieldRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.RaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceServiceImplCloneTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceMapper raceMapper;
    @Mock RaceRegistrationFieldRepository registrationFieldRepository;
    @Mock UserRepository userRepository;

    private RaceServiceImpl service;
    private final UUID sourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RaceServiceImpl(raceRepository, raceMapper, registrationFieldRepository, userRepository);
        lenient().when(raceMapper.toDto(any(Race.class))).thenReturn(new RaceDto());
        lenient().when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Race sourceRace() {
        Race race = new Race();
        race.setId(sourceId);
        race.setName("Source Marathon");
        race.setRaceType(Race.RaceType.MARATHON);
        race.setStatus(Race.RaceStatus.COMPLETED);
        race.setRaceDate(LocalDate.now().minusMonths(1));
        race.setDistanceKm(new BigDecimal("42.2"));
        race.setCurrency("USD");

        race.getCategories().add(RaceCategory.builder().race(race).name("Open").build());
        race.getCheckpoints().add(Checkpoint.builder().race(race).name("Start")
            .checkpointType(Checkpoint.CheckpointType.START).orderIndex(0).build());
        return race;
    }

    @Test
    void cloneCopiesConfigurationAndResetsLifecycleFields() {
        when(raceRepository.findById(sourceId)).thenReturn(Optional.of(sourceRace()));

        RaceCloneRequest request = new RaceCloneRequest();
        request.setName("Source Marathon 2027");
        request.setRaceDate(LocalDate.now().plusMonths(6));

        service.clone(sourceId, request);

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceRepository).save(captor.capture());
        Race saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Source Marathon 2027");
        assertThat(saved.getStatus()).isEqualTo(Race.RaceStatus.DRAFT);
        assertThat(saved.getArchivedAt()).isNull();
        assertThat(saved.getResultsPublishedAt()).isNull();
        assertThat(saved.getCategories()).hasSize(1);
        assertThat(saved.getCheckpoints()).hasSize(1);
        assertThat(saved.getCheckpoints().iterator().next().getRace()).isSameAs(saved);
    }

    @Test
    void archiveSetsTimestampAndRestoreClearsIt() {
        Race race = sourceRace();
        when(raceRepository.findById(sourceId)).thenReturn(Optional.of(race));

        service.archive(sourceId);
        assertThat(race.getArchivedAt()).isNotNull();

        service.restore(sourceId);
        assertThat(race.getArchivedAt()).isNull();
    }
}
