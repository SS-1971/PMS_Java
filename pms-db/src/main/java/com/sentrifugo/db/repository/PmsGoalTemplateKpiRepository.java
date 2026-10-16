package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsGoalTemplateKpiEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PmsGoalTemplateKpiRepository extends JpaRepository<PmsGoalTemplateKpiEntity, UUID> {

    List<PmsGoalTemplateKpiEntity> findByTemplateIdOrderByDisplayOrderAsc(UUID templateId);

    /** Batch lookup used when a caller orders KPIs for several templates at once, to avoid one query per template. */
    List<PmsGoalTemplateKpiEntity> findByTemplateIdInOrderByDisplayOrderAsc(Collection<UUID> templateIds);

    void deleteByTemplateId(UUID templateId);

    boolean existsByKpiId(UUID kpiId);
}
