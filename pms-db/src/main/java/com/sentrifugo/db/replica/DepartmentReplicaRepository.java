package com.sentrifugo.db.replica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentReplicaRepository extends JpaRepository<DepartmentReplica, String> {

    List<DepartmentReplica> findByOrganisationIdAndDeletedFalseOrderByNameAsc(String organisationId);

    List<DepartmentReplica> findByIdInAndOrganisationId(List<String> ids, String organisationId);
}
