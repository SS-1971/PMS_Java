package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KraRepository extends JpaRepository<Kra, UUID> {

    List<Kra> findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(String organisationId);

    Optional<Kra> findByIdAndOrganisationIdAndDeletedOnIsNull(UUID id, String organisationId);

    @Query("select k from Kra k where k.organisationId = :orgId and k.deletedOn is null "
            + "and lower(k.name) = lower(:name) and (:excludeId is null or k.id <> :excludeId)")
    Optional<Kra> findDuplicate(@Param("orgId") String organisationId, @Param("name") String name,
                                @Param("excludeId") UUID excludeId);
}
