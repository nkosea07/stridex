package com.racetracker.dto.notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationPreferenceDto {
    private boolean emailEnabled = true;
    private boolean pushEnabled = true;
    private boolean smsEnabled = true;
    private boolean whatsappEnabled = true;
}
