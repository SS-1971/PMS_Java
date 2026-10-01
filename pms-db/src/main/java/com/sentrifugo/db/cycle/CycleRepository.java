package com.sentrifugo.db.cycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CycleRepository extends JpaRepository<Cycle, UUID>, JpaSpecificationExecutor<Cycle> {

    Optional<Cycle> findByIdAndOrganisationIdAndDeletedOnIsNull(UUID id, String organisationId);

    boolean existsByCycleCode(String cycleCode);
}
