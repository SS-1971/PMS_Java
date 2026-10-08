package com.sentrifugo.db.repository;

import com.sentrifugo.db.entity.PmsGoalTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import com.sentrifugo.db.enums.PmsTemplateStatus;

import java.util.UUID;

/** The screen 2.1 list filters go through {@code PmsGoalTemplateSpecifications}. */
public interface PmsGoalTemplateRepository extends JpaRepository<PmsGoalTemplateEntity, UUID>,
        JpaSpecificationExecutor<PmsGoalTemplateEntity> {

    /** Organisation-scoped lookup: the way to load a row without crossing tenants. */
    Optional<PmsGoalTemplateEntity> findByIdAndOrganisationId(UUID id, String organisationId);

    /** The active template a designation (role) follows for a financial year. */
    Optional<PmsGoalTemplateEntity> findFirstByOrganisationIdAndRoleIdAndFinancialYearAndStatus(
            String organisationId, String roleId, String financialYear, PmsTemplateStatus status);

    /** Every (kept) template of an organisation for a financial year, used by the "copy template" feature. */
    List<PmsGoalTemplateEntity> findByOrganisationIdAndFinancialYearAndIsActiveTrue(
            String organisationId, String financialYear);

    /** Whether a role already has a template (any status) in a financial year, so a copy can skip it. */
    boolean existsByOrganisationIdAndRoleIdAndFinancialYearAndIsActiveTrue(
            String organisationId, String roleId, String financialYear);
}
