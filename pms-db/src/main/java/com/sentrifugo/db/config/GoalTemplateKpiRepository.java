package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoalTemplateKpiRepository extends JpaRepository<GoalTemplateKpi, UUID> {

    /** True if `kpiId` is used by any non-deleted goal template (PMS_KPI_IN_USE). */
    boolean existsByKpiIdAndTemplateKraTemplateDeletedOnIsNull(UUID kpiId);
}
