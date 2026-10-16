package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsGoalChangeRequestEntity;
import com.sentrifugo.db.enums.PmsChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PmsGoalChangeRequestRepository extends JpaRepository<PmsGoalChangeRequestEntity, UUID> {

    /** Every request on a sheet, oldest first. */
    List<PmsGoalChangeRequestEntity> findByAssignmentIdOrderByRequestedOnAscRevisionNoAsc(UUID assignmentId);

    /** Batch lookup used when a caller processes several sheets at once, to avoid one query per sheet. */
    List<PmsGoalChangeRequestEntity> findByAssignmentIdIn(Collection<UUID> assignmentIds);

    List<PmsGoalChangeRequestEntity> findByAssignmentIdAndStatus(UUID assignmentId, PmsChangeRequestStatus status);

    boolean existsByAssignmentIdAndKpiIdAndStatus(UUID assignmentId, UUID kpiId, PmsChangeRequestStatus status);

    long countByAssignmentIdAndKpiId(UUID assignmentId, UUID kpiId);
}
