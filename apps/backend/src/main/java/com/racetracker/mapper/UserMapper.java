package com.racetracker.mapper;

import com.racetracker.dto.auth.UpdateProfileRequest;
import com.racetracker.dto.auth.UserDto;
import com.racetracker.model.UserCapability;
import com.racetracker.model.UserCapabilityType;
import com.racetracker.model.User;
import org.mapstruct.Mapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "capabilities", expression = "java(toCapabilityTypes(user.getCapabilities()))")
    UserDto toDto(User user);

    default java.util.Set<UserCapabilityType> toCapabilityTypes(java.util.Set<UserCapability> capabilities) {
        if (capabilities == null) {
            return java.util.Set.of();
        }
        return capabilities.stream()
            .filter(capability -> Boolean.TRUE.equals(capability.getActive()))
            .map(UserCapability::getCapability)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProfile(UpdateProfileRequest request, @MappingTarget User user);
}
