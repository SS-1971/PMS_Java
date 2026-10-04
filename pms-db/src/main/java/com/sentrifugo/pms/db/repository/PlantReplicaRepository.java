package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PlantReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantReplicaRepository extends JpaRepository<PlantReplicaEntity, String> {

    List<PlantReplicaEntity> findByOrganisationIdAndDeletedFalseOrderByNameAsc(String organisationId);

    List<PlantReplicaEntity> findByIdInAndOrganisationId(List<String> ids, String organisationId);
}
