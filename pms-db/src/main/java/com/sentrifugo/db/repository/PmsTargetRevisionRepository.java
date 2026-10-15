package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsTargetRevisionEntity;

public interface PmsTargetRevisionRepository extends JpaRepository<PmsTargetRevisionEntity, UUID> {

    Optional<PmsTargetRevisionEntity> findFirstByAssignmentIdOrderByRequestedOnDesc(UUID assignmentId);

    /** Every revision still waiting for the employee, so a newer one can supersede them. */
    java.util.List<PmsTargetRevisionEntity> findByAssignmentIdAndStatusAndAcknowledgedOnIsNull(
            UUID assignmentId, com.sentrifugo.db.enums.PmsRevisionStatus status);

    /** The revision the employee has not acknowledged yet (screen 4.5). */
    Optional<PmsTargetRevisionEntity> findFirstByAssignmentIdAndStatusAndAcknowledgedOnIsNullOrderByRequestedOnDesc(
            UUID assignmentId, com.sentrifugo.db.enums.PmsRevisionStatus status);
}
