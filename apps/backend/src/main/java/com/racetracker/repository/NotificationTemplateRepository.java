package com.racetracker.repository;

import com.racetracker.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findAllByOrderByCodeAscLocaleAsc();

    Optional<NotificationTemplate> findByCodeAndLocaleAndEnabledTrue(String code, String locale);

    boolean existsByCodeAndLocale(String code, String locale);
}
