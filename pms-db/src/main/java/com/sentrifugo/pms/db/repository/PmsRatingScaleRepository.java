package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsRatingScaleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PmsRatingScaleRepository extends JpaRepository<PmsRatingScaleEntity, UUID> {

    List<PmsRatingScaleEntity> findByOrganisationIdOrderByCreatedDateAsc(String organisationId);

    Optional<PmsRatingScaleEntity> findByIdAndOrganisationId(UUID id, String organisationId);
}
