package com.racetracker.repository;

import com.racetracker.model.Notification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = {"user"})
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findTop200ByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"user"})
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTitle(UUID userId, String title);

    long countByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
        "delete from Notification n where n.subjectParticipantId in :participantIds")
    int deleteBySubjectParticipantIds(@org.springframework.data.repository.query.Param("participantIds")
                                      java.util.Collection<UUID> participantIds);
}
