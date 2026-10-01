package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingScaleRepository extends JpaRepository<RatingScale, UUID> {

    List<RatingScale> findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(String organisationId);

    Optional<RatingScale> findByIdAndOrganisationIdAndDeletedOnIsNull(UUID id, String organisationId);
}
