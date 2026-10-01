package com.sentrifugo.db.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GoalTemplateCompetencyRepository extends JpaRepository<GoalTemplateCompetency, UUID> {

    /** True if `competencyId` is used by any non-deleted goal template (PMS_COMPETENCY_IN_USE). */
    boolean existsByCompetencyIdAndTemplateDeletedOnIsNull(UUID competencyId);
}
