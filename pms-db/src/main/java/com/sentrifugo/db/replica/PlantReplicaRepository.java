package com.sentrifugo.db.replica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlantReplicaRepository extends JpaRepository<PlantReplica, String> {

    List<PlantReplica> findByOrganisationIdAndDeletedFalseOrderByNameAsc(String organisationId);

    List<PlantReplica> findByIdInAndOrganisationId(List<String> ids, String organisationId);
}
