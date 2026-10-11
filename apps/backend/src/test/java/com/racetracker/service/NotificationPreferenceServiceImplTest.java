package com.racetracker.service;

import com.racetracker.dto.notification.NotificationPreferenceDto;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.NotificationPreference;
import com.racetracker.repository.NotificationPreferenceRepository;
import com.racetracker.service.impl.NotificationPreferenceServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceImplTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void defaultsToAllChannelsEnabledWhenNoPreferenceRow() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        when(repository.findById(userId)).thenReturn(Optional.empty());
        NotificationPreferenceServiceImpl service = new NotificationPreferenceServiceImpl(repository);

        assertThat(service.get(userId).isEmailEnabled()).isTrue();
        assertThat(service.isChannelEnabled(userId, NotificationDelivery.Channel.SMS)).isTrue();
    }

    @Test
    void updatePersistsAndDisablesChannels() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        NotificationPreferenceServiceImpl service = new NotificationPreferenceServiceImpl(repository);

        NotificationPreferenceDto dto = new NotificationPreferenceDto();
        dto.setEmailEnabled(false);
        service.update(userId, dto);

        when(repository.findById(userId)).thenReturn(Optional.of(NotificationPreference.builder()
            .userId(userId).emailEnabled(false).build()));
        assertThat(service.isChannelEnabled(userId, NotificationDelivery.Channel.EMAIL)).isFalse();
        assertThat(service.isChannelEnabled(userId, NotificationDelivery.Channel.IN_APP)).isTrue();
    }
}
