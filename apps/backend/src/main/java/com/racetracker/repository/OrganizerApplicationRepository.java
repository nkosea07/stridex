package com.racetracker.repository;

import com.racetracker.model.OrganizerApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrganizerApplicationRepository extends JpaRepository<OrganizerApplication, UUID> {
    boolean existsByApplicantId(UUID applicantId);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    Page<OrganizerApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"applicant", "reviewedBy"})
    Page<OrganizerApplication> findByStatusOrderByCreatedAtDesc(
        OrganizerApplication.ApplicationStatus status,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from OrganizerApplication application join fetch application.applicant where application.id = :id")
    Optional<OrganizerApplication> findForDecisionById(@Param("id") UUID id);
}
