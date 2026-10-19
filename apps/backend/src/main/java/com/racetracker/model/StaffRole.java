package com.racetracker.model;

public enum StaffRole {
    OFFICIAL,
    VOLUNTEER;

    public User.UserRole toUserRole() {
        return User.UserRole.valueOf(name());
    }
}
