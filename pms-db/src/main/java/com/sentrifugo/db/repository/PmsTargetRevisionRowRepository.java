package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsTargetRevisionRowEntity;

public interface PmsTargetRevisionRowRepository extends JpaRepository<PmsTargetRevisionRowEntity, UUID> {

    List<PmsTargetRevisionRowEntity> findByRevisionId(UUID revisionId);

    /** Batch lookup used when a caller processes several revisions at once, to avoid one query per revision. */
    List<PmsTargetRevisionRowEntity> findByRevisionIdIn(Collection<UUID> revisionIds);
}
