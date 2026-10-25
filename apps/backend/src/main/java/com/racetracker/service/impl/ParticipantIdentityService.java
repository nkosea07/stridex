package com.racetracker.service.impl;

import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ParticipantIdentityService {

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;

    public ParticipantIdentityService(ParticipantRepository participantRepository, RaceRepository raceRepository) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
    }

    public void assign(Participant participant) {
        if (participant.getRace() == null || participant.getRace().getId() == null) {
            return;
        }
        Race race = raceRepository.findByIdForUpdate(participant.getRace().getId())
            .orElseThrow(() -> new com.racetracker.exception.ResourceNotFoundException("Race not found"));
        participant.setRace(race);

        if (participant.getAthleteCode() == null || participant.getAthleteCode().isBlank()) {
            participant.setAthleteCode(nextAthleteCode());
        }
        if (participant.getBibNumber() == null || participant.getBibNumber().isBlank()) {
            participant.setBibNumber(nextBib(race, participant));
        }
    }

    private String nextAthleteCode() {
        String code;
        do {
            code = "ATH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (participantRepository.existsByAthleteCode(code));
        return code;
    }

    private String nextBib(Race race, Participant participant) {
        String prefix = participant.getCategory() != null && participant.getCategory().getBibPrefix() != null
            ? participant.getCategory().getBibPrefix()
            : race.getBibPrefix();
        if (prefix == null) {
            prefix = "";
        }
        int width = race.getBibWidth() == null ? 4 : Math.max(1, Math.min(10, race.getBibWidth()));
        int sequence = race.getNextBibSequence() == null ? 1 : Math.max(1, race.getNextBibSequence());
        for (int attempts = 0; attempts < 100000; attempts++) {
            String candidate = prefix + String.format("%0" + width + "d", sequence);
            if (!participantRepository.existsByBibNumber(candidate)) {
                race.setNextBibSequence(sequence + 1);
                raceRepository.save(race);
                return candidate;
            }
            sequence++;
        }
        throw new com.racetracker.exception.ConflictException("Could not allocate a unique bib number",
            "BIB_ALLOCATION_FAILED");
    }
}
