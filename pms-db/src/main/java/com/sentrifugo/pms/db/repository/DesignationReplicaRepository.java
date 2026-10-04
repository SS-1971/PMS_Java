package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.DesignationReplicaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DesignationReplicaRepository extends JpaRepository<DesignationReplicaEntity, String> {

    List<DesignationReplicaEntity> findByOrganisationIdAndDeletedFalseOrderByNameAsc(String organisationId);

    List<DesignationReplicaEntity> findByOrganisationIdAndDepartmentIdAndDeletedFalseOrderByNameAsc(
            String organisationId, String departmentId);
}
