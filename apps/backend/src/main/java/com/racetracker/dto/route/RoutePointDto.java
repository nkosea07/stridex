package com.racetracker.dto.route;

import java.math.BigDecimal;

public record RoutePointDto(int sequence, BigDecimal latitude, BigDecimal longitude, BigDecimal elevation) {
}
