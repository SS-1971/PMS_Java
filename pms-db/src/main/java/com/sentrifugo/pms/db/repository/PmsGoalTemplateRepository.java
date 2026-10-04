package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsGoalTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PmsGoalTemplateRepository extends JpaRepository<PmsGoalTemplateEntity, UUID> {

    Optional<PmsGoalTemplateEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    @Query("select t from PmsGoalTemplateEntity t where t.organisationId = :orgId "
            + "and (:financialYear is null or t.financialYear = :financialYear) "
            + "and (:departmentId is null or t.departmentId = :departmentId) "
            + "and (:search is null or lower(t.name) like lower(concat('%', :search, '%'))) "
            + "order by t.createdDate desc")
    List<PmsGoalTemplateEntity> search(@Param("orgId") String organisationId,
                                       @Param("financialYear") Integer financialYear,
                                       @Param("departmentId") String departmentId,
                                       @Param("search") String search);

    @Query("select t from PmsGoalTemplateEntity t where t.organisationId = :orgId "
            + "and t.name = :name and t.financialYear = :financialYear and t.designationId = :designationId "
            + "and t.status <> com.sentrifugo.pms.db.enums.PmsTemplateStatus.INACTIVE "
            + "and (:excludeId is null or t.id <> :excludeId)")
    Optional<PmsGoalTemplateEntity> findDuplicate(@Param("orgId") String organisationId,
                                                  @Param("name") String name,
                                                  @Param("financialYear") int financialYear,
                                                  @Param("designationId") String designationId,
                                                  @Param("excludeId") UUID excludeId);
}
