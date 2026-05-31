package com.racetracker.mapper;

import com.racetracker.dto.auth.UpdateProfileRequest;
import com.racetracker.dto.auth.UserDto;
import com.racetracker.model.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProfile(UpdateProfileRequest request, @MappingTarget User user);
}
