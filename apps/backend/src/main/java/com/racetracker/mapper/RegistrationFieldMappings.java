package com.racetracker.mapper;

import com.racetracker.dto.participant.RaceRegistrationFieldDto;
import com.racetracker.model.RaceRegistrationField;

import java.util.List;

public final class RegistrationFieldMappings {

    private RegistrationFieldMappings() {
    }

    public static RaceRegistrationFieldDto toDto(RaceRegistrationField field) {
        RaceRegistrationFieldDto dto = new RaceRegistrationFieldDto();
        dto.setId(field.getId());
        dto.setRaceId(field.getRace() != null ? field.getRace().getId() : null);
        dto.setFieldKey(field.getFieldKey());
        dto.setLabel(field.getLabel());
        dto.setFieldType(field.getFieldType() != null ? field.getFieldType().name() : null);
        dto.setRequired(Boolean.TRUE.equals(field.getRequired()));
        dto.setOptions(field.getOptions() != null ? field.getOptions() : List.of());
        dto.setDisplayOrder(field.getDisplayOrder());
        dto.setActive(Boolean.TRUE.equals(field.getActive()));
        dto.setCreatedAt(field.getCreatedAt());
        return dto;
    }
}
