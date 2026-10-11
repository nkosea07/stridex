package com.racetracker.service.impl;

import com.racetracker.service.RaceCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RaceCompletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RaceCompletionScheduler.class);

    private final RaceCompletionService raceCompletionService;

    public RaceCompletionScheduler(RaceCompletionService raceCompletionService) {
        this.raceCompletionService = raceCompletionService;
    }

    @Scheduled(
        fixedDelayString = "${racetracker.races.completion-scan-interval-ms:300000}",
        initialDelayString = "${racetracker.races.completion-initial-delay-ms:60000}"
    )
    public void completeDueRaces() {
        try {
            int completed = raceCompletionService.autoCompleteDueRaces();
            if (completed > 0) {
                log.info("Auto-completed {} race(s) past their race date", completed);
            }
        } catch (RuntimeException ex) {
            log.error("Race auto-completion scan failed", ex);
        }
    }
}
