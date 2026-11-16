package com.racetracker.service;

import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;

import java.util.List;
import java.util.UUID;

public interface ResultService {
    List<RaceResultDto> listByRace(UUID raceId);
    RaceResultPageDto listByRacePage(UUID raceId, int page, int size);
    RaceResultPageDto listByRacePage(UUID raceId, int page, int size, Long expectedRevision);
    RaceResultPageDto listPublishedByRacePage(UUID raceId, int page, int size);
    RaceResultPageDto listPublishedByRacePage(UUID raceId, int page, int size, Long expectedRevision);
    RaceResultDto getByRaceAndParticipant(UUID raceId, UUID participantId);
    List<RaceResultDto> getMyResults();

    List<com.racetracker.dto.result.AthleteHistoryEntryDto> getMyHistory();

    List<com.racetracker.dto.result.PersonalBestDto> getMyPersonalBests();
    RaceResultPageDto getMyResultsPage(int page, int size);
    int publish(UUID raceId);

    com.racetracker.dto.result.ResultVersionDto certify(UUID raceId);

    void reopen(UUID raceId, String reason);

    com.racetracker.dto.result.ResultIntegrityDto integrity(UUID raceId);

    java.util.List<com.racetracker.dto.result.ResultVersionDto> versions(UUID raceId);
}
