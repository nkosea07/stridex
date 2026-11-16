package com.racetracker.dto.result;

public record ResultIntegrityDto(
    Integer latestVersion,
    String status,
    String publishedAt,
    String certifiedAt,
    String contentSha256,
    String chainSha256,
    boolean chainValid,
    boolean publishedResultsIntact,
    boolean changedSincePublish
) {
}
