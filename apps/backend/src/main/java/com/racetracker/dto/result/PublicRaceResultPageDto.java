package com.racetracker.dto.result;

import java.util.List;

public record PublicRaceResultPageDto(List<PublicRaceResultDto> items, int page, int size, long totalElements, int totalPages, long revision) { }
