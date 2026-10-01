package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalTemplateRepository extends JpaRepository<GoalTemplate, UUID> {

    Optional<GoalTemplate> findByIdAndOrganisationIdAndDeletedOnIsNull(UUID id, String organisationId);

    @Query("select t from GoalTemplate t where t.organisationId = :orgId and t.deletedOn is null "
            + "and (:financialYear is null or t.financialYear = :financialYear) "
            + "and (:departmentId is null or t.departmentId = :departmentId) "
            + "and (:search is null or lower(t.name) like lower(concat('%', :search, '%')))"
            + " order by t.createdOn desc")
    List<GoalTemplate> search(@Param("orgId") String organisationId, @Param("financialYear") Integer financialYear,
                              @Param("departmentId") String departmentId, @Param("search") String search);

    @Query("select t from GoalTemplate t where t.organisationId = :orgId and t.deletedOn is null "
            + "and t.name = :name and t.financialYear = :financialYear and t.designationId = :designationId "
            + "and t.status <> com.sentrifugo.db.config.TemplateStatus.INACTIVE "
            + "and (:excludeId is null or t.id <> :excludeId)")
    Optional<GoalTemplate> findDuplicate(@Param("orgId") String organisationId, @Param("name") String name,
                                        @Param("financialYear") int financialYear,
                                        @Param("designationId") String designationId,
                                        @Param("excludeId") UUID excludeId);
}
