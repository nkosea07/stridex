package com.racetracker.mapper;

import com.racetracker.dto.participant.ParticipantDto;
import com.racetracker.dto.participant.ParticipantCredentialDto;
import com.racetracker.dto.result.ParticipantResultDto;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantCredential;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ParticipantMapper {

    @Mapping(target = "raceId", expression = "java(entity.getRace() != null ? entity.getRace().getId() : null)")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory() != null ? entity.getCategory().getId() : null)")
    @Mapping(target = "gender", expression = "java(entity.getGender() != null ? entity.getGender().name() : null)")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    @Mapping(target = "paymentStatus", expression = "java(entity.getPaymentStatus() != null ? entity.getPaymentStatus().name() : null)")
    @Mapping(target = "teamId", expression = "java(entity.getTeam() != null ? entity.getTeam().getId() : null)")
    @Mapping(target = "answers", ignore = true)
    @Mapping(target = "credentials", expression = "java(mapCredentials(entity.getCredentials()))")
    ParticipantDto toDto(Participant entity);

    @Mapping(target = "raceId", expression = "java(entity.getRace() != null ? entity.getRace().getId() : null)")
    @Mapping(target = "categoryId", expression = "java(entity.getCategory() != null ? entity.getCategory().getId() : null)")
    @Mapping(target = "gender", expression = "java(entity.getGender() != null ? entity.getGender().name() : null)")
    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    ParticipantResultDto toResultDto(Participant entity);

    default List<ParticipantCredentialDto> mapCredentials(Set<ParticipantCredential> credentials) {
        if (credentials == null) {
            return List.of();
        }
        return credentials.stream()
            .sorted(Comparator.comparing(ParticipantCredential::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(credential -> {
                ParticipantCredentialDto dto = new ParticipantCredentialDto();
                dto.setId(credential.getId());
                dto.setType(credential.getCredentialType() != null ? credential.getCredentialType().name() : null);
                dto.setValue(credential.getCredentialValue());
                dto.setStatus(credential.getStatus() != null ? credential.getStatus().name() : null);
                dto.setAssignedAt(credential.getAssignedAt());
                dto.setRevokedAt(credential.getRevokedAt());
                return dto;
            })
            .toList();
    }
}
