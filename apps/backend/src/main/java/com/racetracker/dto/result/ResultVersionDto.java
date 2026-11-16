package com.racetracker.dto.result;

public record ResultVersionDto(
    int version,
    String status,
    int resultCount,
    String contentSha256,
    String chainSha256,
    String publishedAt,
    String certifiedAt
) {
}
