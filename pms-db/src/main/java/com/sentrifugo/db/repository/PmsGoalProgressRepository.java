package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsGoalProgressEntity;
import com.sentrifugo.db.enums.PmsProgressPeriod;

public interface PmsGoalProgressRepository extends JpaRepository<PmsGoalProgressEntity, UUID> {

    List<PmsGoalProgressEntity> findByAssignmentId(UUID assignmentId);

    List<PmsGoalProgressEntity> findByAssignmentIdIn(Collection<UUID> assignmentIds);

    Optional<PmsGoalProgressEntity> findByAssignmentIdAndKpiIdAndPeriod(
            UUID assignmentId, UUID kpiId, PmsProgressPeriod period);
}
