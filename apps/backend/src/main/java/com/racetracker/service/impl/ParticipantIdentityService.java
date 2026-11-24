package com.racetracker.service.impl;

import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ParticipantIdentityService {

    private static final Set<Participant.ParticipantStatus> WITHOUT_PLACE = EnumSet.of(
        Participant.ParticipantStatus.PENDING_REVIEW,
        Participant.ParticipantStatus.WAITLISTED,
        Participant.ParticipantStatus.REJECTED,
        Participant.ParticipantStatus.WITHDRAWN
    );

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
        if ((participant.getBibNumber() == null || participant.getBibNumber().isBlank()) && holdsPlace(participant)) {
            participant.setBibNumber(nextBib(race, participant));
        }
    }

    public static boolean holdsPlace(Participant participant) {
        return participant.getStatus() == null || !WITHOUT_PLACE.contains(participant.getStatus());
    }

    private String nextAthleteCode() {
        String code;
        do {
            code = "ATH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (participantRepository.existsByAthleteCode(code));
        return code;
    }

    private String nextBib(Race race, Participant participant) {
        String categoryPrefix = participant.getCategory() == null ? null : participant.getCategory().getBibPrefix();
        String racePrefix = race.getBibPrefix() == null ? "" : race.getBibPrefix();
        boolean categorySequence = categoryPrefix != null && !categoryPrefix.isBlank() && !categoryPrefix.equals(racePrefix);
        String prefix = categorySequence ? categoryPrefix : racePrefix;
        int width = race.getBibWidth() == null ? 4 : Math.max(1, Math.min(10, race.getBibWidth()));
        int sequence = categorySequence
            ? nextCategorySequence(race.getId(), prefix)
            : race.getNextBibSequence() == null ? 1 : Math.max(1, race.getNextBibSequence());
        for (int attempts = 0; attempts < 100000; attempts++) {
            String candidate = prefix + String.format("%0" + width + "d", sequence);
            if (!participantRepository.existsByRaceIdAndBibNumber(race.getId(), candidate)) {
                if (!categorySequence) {
                    race.setNextBibSequence(sequence + 1);
                    raceRepository.save(race);
                }
                return candidate;
            }
            sequence++;
        }
        throw new com.racetracker.exception.ConflictException("Could not allocate a unique bib number",
            "BIB_ALLOCATION_FAILED");
    }

    private int nextCategorySequence(UUID raceId, String prefix) {
        String pattern = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        int highest = 0;
        for (String bib : participantRepository.findBibNumbersByRaceIdLike(raceId, pattern)) {
            String suffix = bib.substring(prefix.length());
            if (!suffix.isEmpty() && suffix.length() <= 9 && suffix.chars().allMatch(Character::isDigit)) {
                highest = Math.max(highest, Integer.parseInt(suffix));
            }
        }
        return highest + 1;
    }
}
