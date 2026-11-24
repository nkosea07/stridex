package com.racetracker.service;

import com.racetracker.dto.participant.RaceRegistrationRequest;
import com.racetracker.model.Participant;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RaceRegistrationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsACompleteRegistration() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void rejectsValuesTheDatabaseCannotStoreBeforeTheyReachIt() {
        RaceRegistrationRequest request = valid();
        request.getEmergencyContact().setPhone("0".repeat(21));
        request.getEmergencyContact().setName(" ");
        request.setBloodType("AB+POS");
        request.setShirtSize("X".repeat(21));
        request.setMedicalConditions("m".repeat(2001));

        assertThat(paths(validator.validate(request))).containsExactlyInAnyOrder(
            "emergencyContact.phone", "emergencyContact.name", "bloodType", "shirtSize", "medicalConditions");
    }

    private Set<String> paths(Set<ConstraintViolation<RaceRegistrationRequest>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private RaceRegistrationRequest valid() {
        RaceRegistrationRequest request = new RaceRegistrationRequest();
        request.setCategoryId(UUID.randomUUID());
        request.setGender(Participant.Gender.FEMALE);
        request.setDateOfBirth(LocalDate.of(1990, 5, 17));
        RaceRegistrationRequest.EmergencyContact contact = new RaceRegistrationRequest.EmergencyContact();
        contact.setName("Next of Kin");
        contact.setPhone("0700000000");
        contact.setRelationship("Sibling");
        request.setEmergencyContact(contact);
        request.setBloodType("O+");
        return request;
    }
}
