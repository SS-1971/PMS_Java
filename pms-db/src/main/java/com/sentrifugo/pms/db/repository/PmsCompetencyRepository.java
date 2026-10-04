package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsCompetencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PmsCompetencyRepository extends JpaRepository<PmsCompetencyEntity, UUID> {

    List<PmsCompetencyEntity> findByOrganisationIdOrderByCreatedDateAsc(String organisationId);

    Optional<PmsCompetencyEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    @Query("select c from PmsCompetencyEntity c where c.organisationId = :orgId "
            + "and lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    Optional<PmsCompetencyEntity> findDuplicate(@Param("orgId") String organisationId, @Param("name") String name,
                                                @Param("excludeId") UUID excludeId);
}
