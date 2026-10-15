package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsApprovalWorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PmsApprovalWorkflowRepository extends JpaRepository<PmsApprovalWorkflowEntity, UUID> {

    Optional<PmsApprovalWorkflowEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    /** Workflows not removed by Delete (is_active = true), oldest first. */
    List<PmsApprovalWorkflowEntity> findByOrganisationIdAndIsActiveTrueOrderByCreatedDateAsc(String organisationId);

    boolean existsByOrganisationIdAndNameIgnoreCaseAndIsActiveTrue(String organisationId, String name);

    boolean existsByOrganisationIdAndNameIgnoreCaseAndIsActiveTrueAndIdNot(String organisationId, String name,
                                                                          UUID id);
}
