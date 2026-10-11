package com.racetracker.dto.privacy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {
    private String password;
    private String confirmation;
}
