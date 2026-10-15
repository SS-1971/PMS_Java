package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsMidYearReviewEntity;

public interface PmsMidYearReviewRepository extends JpaRepository<PmsMidYearReviewEntity, UUID> {

    Optional<PmsMidYearReviewEntity> findByAssignmentId(UUID assignmentId);

    List<PmsMidYearReviewEntity> findByAssignmentIdIn(Collection<UUID> assignmentIds);
}
