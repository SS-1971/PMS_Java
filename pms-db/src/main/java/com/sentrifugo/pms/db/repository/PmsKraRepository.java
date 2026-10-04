package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsKraEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Inactive (soft-deleted) rows are filtered out by the entity's {@code @SQLRestriction}. */
@Repository
public interface PmsKraRepository extends JpaRepository<PmsKraEntity, UUID> {

    List<PmsKraEntity> findByOrganisationIdOrderByCreatedDateAsc(String organisationId);

    Optional<PmsKraEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    @Query("select k from PmsKraEntity k where k.organisationId = :orgId "
            + "and lower(k.name) = lower(:name) and (:excludeId is null or k.id <> :excludeId)")
    Optional<PmsKraEntity> findDuplicate(@Param("orgId") String organisationId, @Param("name") String name,
                                         @Param("excludeId") UUID excludeId);
}
