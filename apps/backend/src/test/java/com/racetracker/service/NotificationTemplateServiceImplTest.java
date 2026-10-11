package com.racetracker.service;

import com.racetracker.dto.notification.NotificationTemplateDto;
import com.racetracker.dto.notification.NotificationTemplateRequest;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.NotificationTemplate;
import com.racetracker.repository.NotificationTemplateRepository;
import com.racetracker.service.impl.NotificationTemplateServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationTemplateServiceImplTest {

    private NotificationTemplateRequest request() {
        NotificationTemplateRequest request = new NotificationTemplateRequest();
        request.setCode("notification.emergency");
        request.setSubject("Emergency");
        request.setBody("An athlete needs help");
        return request;
    }

    @Test
    void createRejectsDuplicateCodeAndLocale() {
        NotificationTemplateRepository repository = mock(NotificationTemplateRepository.class);
        when(repository.existsByCodeAndLocale("notification.emergency", "en")).thenReturn(true);
        NotificationTemplateServiceImpl service = new NotificationTemplateServiceImpl(repository);

        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void resolveReturnsEnabledTemplateBody() {
        NotificationTemplateRepository repository = mock(NotificationTemplateRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findByCodeAndLocaleAndEnabledTrue("notification.emergency", "en"))
            .thenReturn(Optional.of(NotificationTemplate.builder()
                .id(id).code("notification.emergency").locale("en")
                .subject("Emergency").body("Help needed").enabled(true).build()));
        NotificationTemplateServiceImpl service = new NotificationTemplateServiceImpl(repository);

        assertThat(service.resolve("notification.emergency", null))
            .hasValueSatisfying(template -> assertThat(template.body()).isEqualTo("Help needed"));
        assertThat(service.resolve(null, "en")).isEmpty();
    }

    @Test
    void createPersistsTemplate() {
        NotificationTemplateRepository repository = mock(NotificationTemplateRepository.class);
        when(repository.existsByCodeAndLocale(any(), any())).thenReturn(false);
        when(repository.save(any(NotificationTemplate.class))).thenAnswer(inv -> {
            NotificationTemplate template = inv.getArgument(0);
            template.setId(UUID.randomUUID());
            return template;
        });
        NotificationTemplateServiceImpl service = new NotificationTemplateServiceImpl(repository);

        NotificationTemplateDto dto = service.create(request());

        assertThat(dto.getCode()).isEqualTo("notification.emergency");
        assertThat(dto.getLocale()).isEqualTo("en");
        assertThat(dto.getEnabled()).isTrue();
    }
}
