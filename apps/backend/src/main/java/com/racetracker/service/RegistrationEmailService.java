package com.racetracker.service;

import com.racetracker.model.Participant;

public interface RegistrationEmailService {

    void sendRegistrationReceived(Participant participant);

    void sendRegistrationApproved(Participant participant);

    void sendRegistrationWaitlisted(Participant participant);

    void sendRegistrationRejected(Participant participant);
}
