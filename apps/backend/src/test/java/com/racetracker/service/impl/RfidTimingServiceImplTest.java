package com.racetracker.service.impl;

import com.racetracker.dto.rfid.GunTimeRequest;
import com.racetracker.dto.rfid.RfidReprocessRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfidTimingServiceImplTest {

    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final RfidReadEventRepository eventRepository = mock(RfidReadEventRepository.class);
    private final RaceAccessGuard guard = mock(RaceAccessGuard.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final com.racetracker.service.TimingRegate regate = mock(com.racetracker.service.TimingRegate.class);
    private RfidTimingServiceImpl service;
    private final UUID raceId = UUID.randomUUID();
    private Race race;

    @BeforeEach
    void setUp() {
        service = new RfidTimingServiceImpl(raceRepository, eventRepository, guard, audit, org.mockito.Mockito.mock(com.racetracker.service.ResultLock.class), regate);
        race = Race.builder().id(raceId).status(Race.RaceStatus.IN_PROGRESS).build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
    }

    @Test
    void reprocessRequeuesOnlyTheRequestedStatusesAndIsAudited() {
        RfidReprocessRequest request = new RfidReprocessRequest();
        request.setStatuses(List.of(RfidReprocessRequest.Scope.UNKNOWN_TAG, RfidReprocessRequest.Scope.IGNORED));
        when(eventRepository.requeueByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(42);

        assertThat(service.reprocess(raceId, request, "admin@example.com").requeued()).isEqualTo(42);

        verify(guard).assertCanManage(raceId);
        verify(eventRepository).requeueByRaceIdAndStatusIn(raceId,
            EnumSet.of(RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG, RfidReadEvent.RfidReadEventStatus.IGNORED));
        verify(audit).recordForActor(eq("RFID_READS_REPROCESSED"), eq("admin@example.com"), eq("RACE"),
            eq(raceId.toString()), eq("SUCCESS"), any());
    }

    @Test
    void gunTimeCorrectionIsValidatedAndAudited() {
        GunTimeRequest request = new GunTimeRequest();
        request.setStartedAt(OffsetDateTime.parse("2026-11-15T06:07:12Z"));
        request.setReason("Gun fired seven minutes late");

        when(regate.apply(raceId, null, request.getStartedAt())).thenReturn(new com.racetracker.service.TimingRegate.Outcome(3, 1, 12));

        assertThat(service.correctGunTime(raceId, request, "admin@example.com").startedAt())
            .isEqualTo("2026-11-15T06:07:12Z");
        assertThat(race.getStartedAt()).isEqualTo(request.getStartedAt());
        verify(regate).apply(raceId, null, request.getStartedAt());
        verify(audit).recordForActor(eq("RACE_GUN_TIME_CORRECTED"), eq("admin@example.com"), eq("RACE"),
            eq(raceId.toString()), eq("SUCCESS"), any());
    }

    @Test
    void gunTimeCannotBeInTheFutureOrSetOnADraftRace() {
        GunTimeRequest future = new GunTimeRequest();
        future.setStartedAt(OffsetDateTime.now().plusHours(1));
        future.setReason("typo");
        assertThatThrownBy(() -> service.correctGunTime(raceId, future, "a"))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("GUN_TIME_IN_FUTURE");

        race.setStatus(Race.RaceStatus.DRAFT);
        GunTimeRequest past = new GunTimeRequest();
        past.setStartedAt(OffsetDateTime.now().minusHours(1));
        past.setReason("x");
        assertThatThrownBy(() -> service.correctGunTime(raceId, past, "a"))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("GUN_TIME_NOT_ALLOWED");
        verify(raceRepository, never()).save(any());
    }

    @Test
    void unmatchedTagsSummarizeReadsPerTag() {
        when(eventRepository.findUnmatchedTagSummary(eq(raceId), any(Pageable.class))).thenReturn(List.<Object[]>of(
            new Object[] {"E200ABCD", 37L, OffsetDateTime.parse("2026-11-15T06:30:00Z"), OffsetDateTime.parse("2026-11-15T06:30:04Z")}));

        assertThat(service.unmatchedTags(raceId)).singleElement().satisfies(tag -> {
            assertThat(tag.rfidTag()).isEqualTo("E200ABCD");
            assertThat(tag.reads()).isEqualTo(37);
        });
        verify(guard).assertCanReadStaff(raceId);
    }
}
