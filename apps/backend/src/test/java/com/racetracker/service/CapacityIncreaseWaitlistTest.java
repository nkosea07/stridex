package com.racetracker.service;

import com.racetracker.dto.race.RaceCategoryRequest;
import com.racetracker.dto.race.RaceUpdateRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.mapper.RaceMapper;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.repository.RaceRegistrationFieldRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.RaceCategoryServiceImpl;
import com.racetracker.service.impl.RaceServiceImpl;
import com.racetracker.service.impl.RegistrationCapacity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CapacityIncreaseWaitlistTest {

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RaceCategoryRepository categoryRepository = mock(RaceCategoryRepository.class);
    private final RaceMapper raceMapper = mock(RaceMapper.class);
    private final RegistrationCapacity capacity = mock(RegistrationCapacity.class);
    private final UUID raceId = UUID.randomUUID();

    private RaceServiceImpl raceService() {
        when(raceMapper.toDto(any(Race.class))).thenReturn(new RaceDto());
        when(raceRepository.save(any(Race.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return new RaceServiceImpl(raceRepository, raceMapper, mock(RaceRegistrationFieldRepository.class),
            mock(UserRepository.class), mock(RaceCompletionService.class), mock(RaceAccessGuard.class),
            mock(AuditEventService.class), capacity);
    }

    private Race race(Integer maxParticipants) {
        Race race = new Race();
        race.setId(raceId);
        race.setMaxParticipants(maxParticipants);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        return race;
    }

    @Test
    void raisingOrRemovingTheRaceLimitOffersPlacesToTheWaitlist() {
        RaceServiceImpl service = raceService();
        race(100);
        RaceUpdateRequest raise = new RaceUpdateRequest();
        raise.setMaxParticipants(120);
        service.update(raceId, raise);
        verify(capacity).fillFreedPlaces(raceId);
    }

    @Test
    void loweringOrKeepingTheRaceLimitLeavesTheWaitlistAlone() {
        RaceServiceImpl service = raceService();
        race(100);
        RaceUpdateRequest lower = new RaceUpdateRequest();
        lower.setMaxParticipants(80);
        service.update(raceId, lower);
        RaceUpdateRequest rename = new RaceUpdateRequest();
        rename.setName("Renamed");
        service.update(raceId, rename);
        verify(capacity, never()).fillFreedPlaces(any());
    }

    @Test
    void raisingACategoryLimitOffersPlacesToTheWaitlist() {
        Race race = new Race();
        race.setId(raceId);
        UUID categoryId = UUID.randomUUID();
        RaceCategory category = RaceCategory.builder().id(categoryId).race(race).name("Elite").maxParticipants(20).build();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(RaceCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RaceCategoryRequest request = new RaceCategoryRequest();
        request.setName("Elite");
        request.setMaxParticipants(30);

        new RaceCategoryServiceImpl(raceRepository, categoryRepository, capacity).update(raceId, categoryId, request);

        verify(capacity).fillFreedPlaces(raceId);
    }
}
