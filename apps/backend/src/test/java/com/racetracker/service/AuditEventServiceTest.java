package com.racetracker.service;

import com.racetracker.model.AuditEvent;
import com.racetracker.repository.AuditEventRepository;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditEventServiceTest {

    @Test
    void recordDropsCredentialsAndPersonalDataFromMetadata() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditEventService service = new AuditEventService(repository, mock(UserRepository.class));

        service.recordInCurrentTransaction("AUTH", null, "USER", "id", "DENIED", Map.of(
            "status", "invalid",
            "refreshToken", "must-not-persist",
            "totpSecret", "must-not-persist-either",
            "participantEmail", "runner@example.com"
        ));

        org.mockito.ArgumentCaptor<AuditEvent> event = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getMetadata()).containsExactlyEntriesOf(Map.of("status", "invalid"));
    }

    @Test
    void searchBoundsPageSizeAndReturnsStablePageMetadata() {
        AuditEventRepository repository = mock(AuditEventRepository.class);
        AuditEventService service = new AuditEventService(repository, mock(UserRepository.class));
        UUID actorId = UUID.randomUUID();
        when(repository.search(any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        var result = service.search(null, null, actorId, 0, 500);

        assertThat(result.size()).isEqualTo(100);
        assertThat(result.items()).isEmpty();
        org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(null), eq(null), eq(actorId), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
