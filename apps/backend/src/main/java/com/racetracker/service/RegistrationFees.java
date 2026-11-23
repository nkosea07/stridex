package com.racetracker.service;

import com.racetracker.model.Participant;
import com.racetracker.model.Race;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * The entry fee a participant pays right now: the team fee for team entries, the early-bird fee
 * until it ends, otherwise the category fee or the race fee. Category values override race values.
 */
public final class RegistrationFees {

    private RegistrationFees() {
    }

    public static BigDecimal current(Participant participant, Race race) {
        if (race == null) {
            return null;
        }
        if (participant.getTeam() != null) {
            BigDecimal teamFee = participant.getCategory() != null && participant.getCategory().getTeamFee() != null
                ? participant.getCategory().getTeamFee()
                : race.getTeamFee();
            if (teamFee != null && teamFee.signum() > 0) {
                return teamFee;
            }
        }
        if (race.getEarlyBirdEndsAt() != null && !OffsetDateTime.now().isAfter(race.getEarlyBirdEndsAt())) {
            BigDecimal earlyBirdFee = participant.getCategory() != null
                && participant.getCategory().getEarlyBirdFee() != null
                ? participant.getCategory().getEarlyBirdFee()
                : race.getEarlyBirdFee();
            if (earlyBirdFee != null && earlyBirdFee.signum() > 0) {
                return earlyBirdFee;
            }
        }
        return participant.getCategory() != null && participant.getCategory().getRegistrationFee() != null
            ? participant.getCategory().getRegistrationFee()
            : race.getRegistrationFee();
    }

    public static boolean requiresPayment(Participant participant, Race race) {
        BigDecimal fee = current(participant, race);
        return fee != null && fee.signum() > 0;
    }

    /** True when the participant owes an entry fee that has been neither paid nor waived. */
    public static boolean owes(Participant participant, Race race) {
        return requiresPayment(participant, race)
            && participant.getPaymentStatus() != Participant.PaymentStatus.PAID
            && participant.getPaymentStatus() != Participant.PaymentStatus.WAIVED;
    }
}
