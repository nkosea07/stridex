package com.racetracker.mapper;

import com.racetracker.dto.participant.ParticipantDto;
import com.racetracker.model.Participant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ParticipantMapper {

    @Mapping(target = "raceId", expression = "java(entity.getRace() != null ? entity.getRace().getId() : null)")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory() != null ? entity.getCategory().getId() : null)")
    @Mapping(target = "gender", expression = "java(entity.getGender() != null ? entity.getGender().name() : null)")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    ParticipantDto toDto(Participant entity);
}
