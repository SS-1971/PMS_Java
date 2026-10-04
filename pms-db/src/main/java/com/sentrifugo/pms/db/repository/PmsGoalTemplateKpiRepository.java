package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsGoalTemplateKpiEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PmsGoalTemplateKpiRepository extends JpaRepository<PmsGoalTemplateKpiEntity, UUID> {

    /** True if {@code kpiId} is used by any active goal template (PMS_KPI_IN_USE). */
    boolean existsByKpiIdAndTemplateKraTemplateIsActiveTrue(UUID kpiId);
}
