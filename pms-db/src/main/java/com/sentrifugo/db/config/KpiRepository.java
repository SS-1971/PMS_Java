package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KpiRepository extends JpaRepository<Kpi, UUID> {

    List<Kpi> findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(String organisationId);

    Optional<Kpi> findByIdAndOrganisationIdAndDeletedOnIsNull(UUID id, String organisationId);

    long countByKraIdAndDeletedOnIsNull(UUID kraId);

    @Query("select k from Kpi k where k.kra.id = :kraId and k.deletedOn is null "
            + "and lower(k.name) = lower(:name) and (:excludeId is null or k.id <> :excludeId)")
    Optional<Kpi> findDuplicate(@Param("kraId") UUID kraId, @Param("name") String name,
                               @Param("excludeId") UUID excludeId);
}
