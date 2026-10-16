package com.sentrifugo.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sentrifugo.db.entity.PmsGoalTransferEntity;

public interface PmsGoalTransferRepository extends JpaRepository<PmsGoalTransferEntity, UUID> {

    Optional<PmsGoalTransferEntity> findByOrganisationIdAndEmployeeUserIdAndFinancialYear(
            String organisationId, String employeeUserId, String financialYear);

    /** Transfers into one manager team, for the banner on screen 3.6. */
    List<PmsGoalTransferEntity> findByOrganisationIdAndFinancialYearAndNewManagerUserId(
            String organisationId, String financialYear, String newManagerUserId);

    List<PmsGoalTransferEntity> findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(
            String organisationId, String financialYear, Collection<String> employeeUserIds);
}
