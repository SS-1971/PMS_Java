package com.sentrifugo.db.replica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DesignationReplicaRepository extends JpaRepository<DesignationReplica, String> {

    List<DesignationReplica> findByOrganisationIdAndDeletedFalseOrderByNameAsc(String organisationId);

    List<DesignationReplica> findByOrganisationIdAndDepartmentIdAndDeletedFalseOrderByNameAsc(
            String organisationId, String departmentId);
}
