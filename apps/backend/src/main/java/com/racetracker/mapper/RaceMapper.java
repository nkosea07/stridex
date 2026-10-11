package com.racetracker.mapper;

import com.racetracker.dto.race.RaceCategoryDto;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RaceMapper {

    @Mapping(target = "raceType", expression = "java(mapRaceType(entity.getRaceType()))")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    @Mapping(target = "participantCount", expression = "java(entity.getParticipants() != null ? entity.getParticipants().size() : 0)")
    @Mapping(target = "registrationFields", ignore = true)
    RaceDto toDto(Race entity);

    @Mapping(target = "raceId", expression = "java(entity.getRace() != null ? entity.getRace().getId() : null)")
    @Mapping(target = "participantCount", expression = "java(entity.getParticipants() != null ? entity.getParticipants().size() : 0)")
    RaceCategoryDto toCategoryDto(RaceCategory entity);

    default String mapRaceType(Race.RaceType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case TEN_K -> "10K";
            case FIVE_K -> "5K";
            default -> type.name();
        };
    }
}
