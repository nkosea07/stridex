package com.racetracker.dto.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RaceResultDtoJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void resultParticipantJsonContainsOnlyResultFields() throws Exception {
        ParticipantResultDto participant = new ParticipantResultDto();
        participant.setId(UUID.randomUUID());
        participant.setRaceId(UUID.randomUUID());
        participant.setCategoryId(UUID.randomUUID());
        participant.setFirstName("Taylor");
        participant.setLastName("Runner");
        participant.setGender("FEMALE");
        participant.setNationality("ZW");
        participant.setBibNumber("A-1");
        participant.setStatus("FINISHED");
        participant.setClubName("City Club");
        participant.setTeamName("Blue Team");

        RaceResultDto result = new RaceResultDto();
        result.setParticipant(participant);

        JsonNode participantJson = objectMapper.readTree(objectMapper.writeValueAsString(result)).get("participant");

        assertThat(participantJson.fieldNames()).toIterable().containsExactlyInAnyOrderElementsOf(Set.of(
            "id", "raceId", "categoryId", "firstName", "lastName", "gender", "nationality", "bibNumber",
            "status", "clubName", "teamName"));
        List.of(
            "email", "phone", "dateOfBirth", "rfidTag", "emergencyContactName", "emergencyContactPhone",
            "emergencyContactRelation", "bloodType", "medicalConditions", "allergies", "paymentStatus",
            "paymentReference", "paymentAmount", "paymentVerifiedAt", "credentials", "answers")
            .forEach(field -> assertThat(participantJson.has(field)).as(field).isFalse());
    }
}
