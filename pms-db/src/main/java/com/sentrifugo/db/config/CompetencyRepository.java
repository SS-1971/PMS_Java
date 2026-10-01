package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompetencyRepository extends JpaRepository<Competency, UUID> {

    List<Competency> findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(String organisationId);

    Optional<Competency> findByIdAndOrganisationIdAndDeletedOnIsNull(UUID id, String organisationId);

    @Query("select c from Competency c where c.organisationId = :orgId and c.deletedOn is null "
            + "and lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    Optional<Competency> findDuplicate(@Param("orgId") String organisationId, @Param("name") String name,
                                       @Param("excludeId") UUID excludeId);
}
