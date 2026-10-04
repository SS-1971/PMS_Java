package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.DepartmentReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentReplicaRepository extends JpaRepository<DepartmentReplicaEntity, String> {

    List<DepartmentReplicaEntity> findByOrganisationIdAndDeletedFalseOrderByNameAsc(String organisationId);

    List<DepartmentReplicaEntity> findByIdInAndOrganisationId(List<String> ids, String organisationId);
}
