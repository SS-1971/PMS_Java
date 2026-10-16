package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsGoalSheetHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PmsGoalSheetHistoryRepository extends JpaRepository<PmsGoalSheetHistoryEntity, UUID> {

    /** The whole trail of one sheet, oldest first. */
    List<PmsGoalSheetHistoryEntity> findByAssignmentIdOrderByOccurredOnAsc(UUID assignmentId);

    /** Batch lookup used when a caller renders several sheets' history at once, to avoid one query per sheet. */
    List<PmsGoalSheetHistoryEntity> findByAssignmentIdInOrderByOccurredOnAsc(Collection<UUID> assignmentIds);

    /** HOD decisions across the organisation for one year, newest first. */
    List<PmsGoalSheetHistoryEntity> findByAssignmentOrganisationIdAndAssignmentFinancialYearAndActorRoleAndActionInOrderByOccurredOnDesc(
            String organisationId, String financialYear, String actorRole, Collection<String> actions);

    /** Rounds a sheet has been through: how many times a given action happened. */
    long countByAssignmentIdAndAction(UUID assignmentId, String action);
}
