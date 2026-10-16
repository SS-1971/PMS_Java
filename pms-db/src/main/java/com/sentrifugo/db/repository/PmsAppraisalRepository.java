package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsAppraisalEntity;

public interface PmsAppraisalRepository extends JpaRepository<PmsAppraisalEntity, UUID> {

    Optional<PmsAppraisalEntity> findByAssignmentId(UUID assignmentId);

    List<PmsAppraisalEntity> findByAssignmentIdIn(Collection<UUID> assignmentIds);

    /**
     * Every appraisal of one organisation and financial year, for the Corporate HR and calibration screens.
     * The assignment is joined in the same query because every caller reads the employee's name, code,
     * department etc. off it for every row; without the join, that lazy association would be loaded with
     * one query per appraisal.
     */
    @EntityGraph(attributePaths = "assignment")
    List<PmsAppraisalEntity> findByAssignment_OrganisationIdAndAssignment_FinancialYear(
            String organisationId, String financialYear);
}
