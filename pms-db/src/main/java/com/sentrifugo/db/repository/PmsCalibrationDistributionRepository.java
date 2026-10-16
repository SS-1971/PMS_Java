package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsCalibrationDistributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PmsCalibrationDistributionRepository extends JpaRepository<PmsCalibrationDistributionEntity, UUID> {

    List<PmsCalibrationDistributionEntity> findByOrganisationIdAndFinancialYear(
            String organisationId, String financialYear);
}
