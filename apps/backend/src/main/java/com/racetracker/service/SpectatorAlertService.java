package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.Participant;

public interface SpectatorAlertService {
    void onStarted(Participant participant);
    void onFinished(Participant participant);
    void onCheckpoint(Participant participant, Checkpoint checkpoint);
}
