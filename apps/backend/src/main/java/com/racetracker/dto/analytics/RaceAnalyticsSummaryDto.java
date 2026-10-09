package com.racetracker.dto.analytics;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class RaceAnalyticsSummaryDto {
    private long totalRegistrations;
    private Map<String, Long> statusCounts = new LinkedHashMap<>();
    private long paidCount;
    private long unpaidCount;
    private long refundedCount;
    private BigDecimal revenue = BigDecimal.ZERO;
    private double completionRate;
}
