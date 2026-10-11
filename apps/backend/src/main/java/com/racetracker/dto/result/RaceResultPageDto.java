package com.racetracker.dto.result;

import java.util.List;

public record RaceResultPageDto(List<RaceResultDto> items, int page, int size, long totalElements, int totalPages, long revision) { }
