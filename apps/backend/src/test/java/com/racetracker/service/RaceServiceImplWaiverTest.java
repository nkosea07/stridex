package com.racetracker.service;

import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceUpdateRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.mapper.RaceMapper;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRegistrationFieldRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.RaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceServiceImplWaiverTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceMapper raceMapper;
    @Mock RaceRegistrationFieldRepository registrationFieldRepository;
    @Mock UserRepository userRepository;

    private RaceServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RaceServiceImpl(raceRepository, raceMapper, registrationFieldRepository, userRepository);
    }

    private RaceCreateRequest createRequest(boolean requireWaiver, String waiverText) {
        RaceCreateRequest request = new RaceCreateRequest();
        request.setName("Test Race");
        request.setRaceType("MARATHON");
        request.setRaceDate(LocalDate.now().plusMonths(1));
        request.setRequireWaiver(requireWaiver);
        request.setWaiverText(waiverText);
        return request;
    }

    @Test
    void createWithWaiverEnabledRequiresText() {
        assertThatThrownBy(() -> service.create(createRequest(true, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Waiver text is required");
    }

    @Test
    void updateEnablingWaiverWithoutTextIsRejected() {
        Race race = new Race();
        race.setId(raceId);
        race.setRequireWaiver(false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        RaceUpdateRequest request = new RaceUpdateRequest();
        request.setRequireWaiver(true);

        assertThatThrownBy(() -> service.update(raceId, request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Waiver text is required");
    }

    @Test
    void updateUnrelatedFieldPreservesExistingWaiver() {
        Race race = new Race();
        race.setId(raceId);
        race.setRequireWaiver(true);
        race.setWaiverText("Existing waiver terms");
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(raceMapper.toDto(any(Race.class))).thenReturn(new RaceDto());

        RaceUpdateRequest request = new RaceUpdateRequest();
        request.setName("Renamed Race");

        assertThatCode(() -> service.update(raceId, request)).doesNotThrowAnyException();
    }
}
