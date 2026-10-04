package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsCycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** List-screen filtering goes through {@code PmsCycleSpecifications}, not derived-query permutations. */
@Repository
public interface PmsCycleRepository extends JpaRepository<PmsCycleEntity, UUID>,
        JpaSpecificationExecutor<PmsCycleEntity> {

    Optional<PmsCycleEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    boolean existsByCycleCode(String cycleCode);
}
