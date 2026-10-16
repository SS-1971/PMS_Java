package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsRatingScaleLevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PmsRatingScaleLevelRepository extends JpaRepository<PmsRatingScaleLevelEntity, UUID> {

    List<PmsRatingScaleLevelEntity> findByRatingScaleIdOrderByRatingValueDesc(UUID ratingScaleId);

    /** Batch lookup used when a caller renders several scales' levels at once, to avoid one query per scale. */
    List<PmsRatingScaleLevelEntity> findByRatingScaleIdInOrderByRatingValueDesc(Collection<UUID> ratingScaleIds);
}
