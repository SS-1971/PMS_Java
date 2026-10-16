package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsAppraisalLineEntity;

public interface PmsAppraisalLineRepository extends JpaRepository<PmsAppraisalLineEntity, UUID> {

    List<PmsAppraisalLineEntity> findByAppraisalId(UUID appraisalId);

    /** Batch lookup used when a caller renders several appraisals' lines at once, to avoid one query per appraisal. */
    List<PmsAppraisalLineEntity> findByAppraisalIdIn(Collection<UUID> appraisalIds);

    void deleteByAppraisalId(UUID appraisalId);
}
