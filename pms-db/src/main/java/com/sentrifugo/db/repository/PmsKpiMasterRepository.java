package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsKpiMasterEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PmsKpiMasterRepository extends JpaRepository<PmsKpiMasterEntity, UUID> {

    /** Organisation-scoped lookup: the way to load a row without crossing tenants. */
    Optional<PmsKpiMasterEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    /**
     * The KPI master list (screen 2.7). The KRA is joined in the same query because every row's response
     * reads the KRA's id and name; without the join that lazy association would be loaded with one query
     * per KPI.
     */
    @EntityGraph(attributePaths = "kra")
    List<PmsKpiMasterEntity> findByOrganisationIdAndIsActiveTrueOrderByCreatedDateAsc(String organisationId);

    List<PmsKpiMasterEntity> findByOrganisationIdAndIdIn(String organisationId, java.util.Collection<UUID> ids);

    boolean existsByKraIdAndIsActiveTrue(UUID kraId);

    boolean existsByKraIdAndNameIgnoreCaseAndIsActiveTrue(UUID kraId, String name);

    boolean existsByKraIdAndNameIgnoreCaseAndIsActiveTrueAndIdNot(UUID kraId, String name, UUID id);
}
