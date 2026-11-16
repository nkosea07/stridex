package com.racetracker.dto.rfid;

public record RfidUnmatchedTagDto(String rfidTag, long reads, String firstReadAt, String lastReadAt) {
}
