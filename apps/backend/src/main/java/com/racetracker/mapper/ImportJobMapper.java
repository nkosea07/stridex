package com.racetracker.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.participant.ImportJobDto;
import com.racetracker.model.ImportJob;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ImportJobMapper {

    ObjectMapper ROW_ERROR_READER = new ObjectMapper();

    @Mapping(source = "race.id", target = "raceId")
    @Mapping(target = "rowErrors", ignore = true)
    ImportJobDto toDto(ImportJob entity);

    @AfterMapping
    default void mapRowErrors(ImportJob entity, @MappingTarget ImportJobDto dto) {
        if (entity.getRowErrors() == null || entity.getRowErrors().isBlank()) {
            return;
        }
        try {
            dto.setRowErrors(ROW_ERROR_READER.readValue(entity.getRowErrors(),
                new TypeReference<List<ImportJobDto.RowError>>() { }));
        } catch (Exception ignored) {
            dto.setRowErrors(List.of());
        }
    }
}
