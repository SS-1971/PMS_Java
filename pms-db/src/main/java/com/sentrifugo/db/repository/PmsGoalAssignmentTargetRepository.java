package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PmsGoalAssignmentTargetRepository extends JpaRepository<PmsGoalAssignmentTargetEntity, UUID> {

    List<PmsGoalAssignmentTargetEntity> findByAssignmentId(UUID assignmentId);

    /** Batch lookup used when a caller processes several assignments at once, to avoid one query per assignment. */
    List<PmsGoalAssignmentTargetEntity> findByAssignmentIdIn(Collection<UUID> assignmentIds);

    void deleteByAssignmentId(UUID assignmentId);

    /** Which of the given assignments have at least one saved target (the others only have a template). */
    @Query("select distinct t.assignment.id from PmsGoalAssignmentTargetEntity t where t.assignment.id in :ids")
    List<UUID> findAssignmentIdsWithTargets(@Param("ids") Collection<UUID> ids);
}
