package com.racetracker.mapper;

import com.racetracker.dto.participant.ImportJobDto;
import com.racetracker.model.ImportJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ImportJobMapper {
    @Mapping(source = "race.id", target = "raceId")
    ImportJobDto toDto(ImportJob entity);
}
