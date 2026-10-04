package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsKpiEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PmsKpiRepository extends JpaRepository<PmsKpiEntity, UUID> {

    List<PmsKpiEntity> findByOrganisationIdOrderByCreatedDateAsc(String organisationId);

    Optional<PmsKpiEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    long countByKraId(UUID kraId);

    @Query("select k from PmsKpiEntity k where k.kra.id = :kraId "
            + "and lower(k.name) = lower(:name) and (:excludeId is null or k.id <> :excludeId)")
    Optional<PmsKpiEntity> findDuplicate(@Param("kraId") UUID kraId, @Param("name") String name,
                                         @Param("excludeId") UUID excludeId);
}
