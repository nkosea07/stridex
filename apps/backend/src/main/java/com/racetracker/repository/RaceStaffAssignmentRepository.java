package com.racetracker.repository;

import com.racetracker.model.RaceStaffAssignment;
import com.racetracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RaceStaffAssignmentRepository extends JpaRepository<RaceStaffAssignment, UUID> {

    Optional<RaceStaffAssignment> findFirstByRaceIdAndUserIdAndActiveTrue(UUID raceId, UUID userId);

    Optional<RaceStaffAssignment> findFirstByRaceIdAndUserIdOrderByAssignedAtDesc(UUID raceId, UUID userId);

    boolean existsByUserIdAndStaffRoleAndActiveTrue(UUID userId, User.UserRole staffRole);

    @Query("""
        select (count(a) > 0)
        from RaceStaffAssignment a
        where a.race.id = :raceId
          and lower(a.user.email) = lower(:email)
          and a.staffRole = :staffRole
          and a.active = true
    """)
    boolean existsActiveByRaceIdAndUserEmailAndStaffRole(
        @Param("raceId") UUID raceId,
        @Param("email") String email,
        @Param("staffRole") User.UserRole staffRole
    );

    @Query(value = """
        select exists (
            select 1
            from checkpoint_volunteers cv
            join checkpoints c on c.id = cv.checkpoint_id
            join users u on u.id = cv.user_id
            where c.race_id = :raceId
              and lower(u.email) = lower(:email)
        )
    """, nativeQuery = true)
    boolean existsCheckpointVolunteerByRaceIdAndUserEmail(
        @Param("raceId") UUID raceId,
        @Param("email") String email
    );

    @Query(value = """
        select exists (
            select 1
            from checkpoint_volunteers cv
            join checkpoints c on c.id = cv.checkpoint_id
            join users u on u.id = cv.user_id
            where c.race_id = :raceId
              and c.id = :checkpointId
              and lower(u.email) = lower(:email)
        )
    """, nativeQuery = true)
    boolean existsCheckpointVolunteerByRaceIdAndCheckpointIdAndUserEmail(
        @Param("raceId") UUID raceId,
        @Param("checkpointId") UUID checkpointId,
        @Param("email") String email
    );
}
