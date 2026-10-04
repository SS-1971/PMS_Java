package com.sentrifugo.pms.db.repository;

import com.sentrifugo.pms.db.entity.PmsGoalTemplateCompetencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PmsGoalTemplateCompetencyRepository extends JpaRepository<PmsGoalTemplateCompetencyEntity, UUID> {

    /** True if {@code competencyId} is used by any active goal template (PMS_COMPETENCY_IN_USE). */
    boolean existsByCompetencyIdAndTemplateIsActiveTrue(UUID competencyId);
}
