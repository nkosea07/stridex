package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RegistrationFieldRequest {

    @NotBlank
    private String label;

    private String fieldKey;

    private String fieldType;

    private Boolean required;

    private List<String> options;

    private Integer displayOrder;

    private Boolean active;
}
