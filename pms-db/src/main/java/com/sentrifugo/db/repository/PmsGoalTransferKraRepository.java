package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsGoalTransferKraEntity;

public interface PmsGoalTransferKraRepository extends JpaRepository<PmsGoalTransferKraEntity, UUID> {

    List<PmsGoalTransferKraEntity> findByTransferIdOrderByPeriodAscDisplayOrderAsc(UUID transferId);

    void deleteByTransferIdAndPeriod(UUID transferId, int period);
}
