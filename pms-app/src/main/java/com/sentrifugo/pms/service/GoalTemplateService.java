package com.sentrifugo.pms.service;

import com.sentrifugo.pms.model.goaltemplate.GoalTemplateDto;
import com.sentrifugo.pms.model.goaltemplate.GoalTemplateDtos;
import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateKpiDto;
import com.sentrifugo.pms.db.dto.PmsGoalTemplateKraDto;
import com.sentrifugo.pms.db.entity.PmsCompetencyEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateCompetencyEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateKpiEntity;
import com.sentrifugo.pms.db.entity.PmsGoalTemplateKraEntity;
import com.sentrifugo.pms.db.entity.PmsKpiEntity;
import com.sentrifugo.pms.db.entity.PmsKraEntity;
import com.sentrifugo.pms.db.enums.PmsTargetType;
import com.sentrifugo.pms.db.enums.PmsTemplateStatus;
import com.sentrifugo.pms.db.mapper.PmsGoalTemplateMapper;
import com.sentrifugo.pms.db.repository.DepartmentReplicaRepository;
import com.sentrifugo.pms.db.repository.DesignationReplicaRepository;
import com.sentrifugo.pms.db.repository.PmsCompetencyRepository;
import com.sentrifugo.pms.db.repository.PmsGoalTemplateRepository;
import com.sentrifugo.pms.db.repository.PmsKpiRepository;
import com.sentrifugo.pms.db.repository.PmsKraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GoalTemplateService {

    private static final BigDecimal WEIGHT_TOTAL = BigDecimal.valueOf(100);
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.01");

    private final PmsGoalTemplateRepository templates;
    private final PmsKraRepository kras;
    private final PmsKpiRepository kpis;
    private final PmsCompetencyRepository competencies;
    private final DepartmentReplicaRepository departments;
    private final DesignationReplicaRepository designations;
    private final PmsGoalTemplateMapper mapper;

    public GoalTemplateService(PmsGoalTemplateRepository templates, PmsKraRepository kras, PmsKpiRepository kpis,
                               PmsCompetencyRepository competencies, DepartmentReplicaRepository departments,
                               DesignationReplicaRepository designations, PmsGoalTemplateMapper mapper) {
        this.templates = templates;
        this.kras = kras;
        this.kpis = kpis;
        this.competencies = competencies;
        this.departments = departments;
        this.designations = designations;
        this.mapper = mapper;
    }

    public List<GoalTemplateDtos.Summary> list(String organisationId, Integer financialYear, String departmentId,
                                               String search) {
        return templates.search(organisationId, financialYear, departmentId, blankToNull(search)).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public GoalTemplateDto get(String organisationId, UUID templateId) {
        return toDto(find(organisationId, templateId));
    }

    @Transactional
    public GoalTemplateDto create(String organisationId, GoalTemplateDtos.UpsertRequest request) {
        PmsTemplateStatus status = PmsTemplateStatus.fromWire(request.basic().status());
        assertNameFree(organisationId, request.basic(), null);
        if (status != PmsTemplateStatus.DRAFT) {
            assertComplete(organisationId, request);
        }

        PmsGoalTemplateEntity template = PmsGoalTemplateEntity.builder()
                .organisationId(organisationId)
                .financialYear(request.basic().financialYear())
                .name(request.basic().name().trim())
                .description(request.basic().description())
                .departmentId(request.basic().departmentId())
                .designationId(request.basic().designationId())
                .effectiveFrom(request.basic().effectiveFrom())
                .status(status)
                .build();
        applyChildren(organisationId, template, request);
        return toDto(templates.save(template));
    }

    @Transactional
    public GoalTemplateDto update(String organisationId, UUID templateId, GoalTemplateDtos.UpsertRequest request) {
        PmsGoalTemplateEntity template = find(organisationId, templateId);
        PmsTemplateStatus status = PmsTemplateStatus.fromWire(request.basic().status());
        assertNameFree(organisationId, request.basic(), templateId);
        if (status != PmsTemplateStatus.DRAFT) {
            assertComplete(organisationId, request);
        }

        template.setFinancialYear(request.basic().financialYear());
        template.setName(request.basic().name().trim());
        template.setDescription(request.basic().description());
        template.setDepartmentId(request.basic().departmentId());
        template.setDesignationId(request.basic().designationId());
        template.setEffectiveFrom(request.basic().effectiveFrom());
        template.setStatus(status);
        applyChildren(organisationId, template, request);
        return toDto(templates.save(template));
    }

    @Transactional
    public GoalTemplateDto setStatus(String organisationId, UUID templateId, String statusWire) {
        PmsGoalTemplateEntity template = find(organisationId, templateId);
        PmsTemplateStatus status = PmsTemplateStatus.fromWire(statusWire);
        if (status == PmsTemplateStatus.ACTIVE) {
            assertComplete(organisationId, toUpsertRequest(template));
        }
        template.setStatus(status);
        return toDto(templates.save(template));
    }

    @Transactional
    public GoalTemplateDto duplicate(String organisationId, UUID templateId) {
        PmsGoalTemplateEntity source = find(organisationId, templateId);
        PmsGoalTemplateEntity copy = PmsGoalTemplateEntity.builder()
                .organisationId(organisationId)
                .financialYear(source.getFinancialYear())
                .name(source.getName() + " (Copy)")
                .description(source.getDescription())
                .departmentId(source.getDepartmentId())
                .designationId(source.getDesignationId())
                .effectiveFrom(source.getEffectiveFrom())
                .status(PmsTemplateStatus.DRAFT)
                .build();
        applyChildren(organisationId, copy, toUpsertRequest(source));
        return toDto(templates.save(copy));
    }

    /** Soft delete by flag (see {@link PmsGoalTemplateEntity}) so the child rows are kept. */
    @Transactional
    public void delete(String organisationId, UUID templateId) {
        PmsGoalTemplateEntity template = find(organisationId, templateId);
        template.setIsActive(false);
        templates.save(template);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private PmsGoalTemplateEntity find(String organisationId, UUID templateId) {
        return templates.findByIdAndOrganisationId(templateId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Goal template not found", "PMS_TEMPLATE_NOT_FOUND"));
    }

    private void assertNameFree(String organisationId, GoalTemplateDtos.Basic basic, UUID excludeId) {
        templates.findDuplicate(organisationId, basic.name().trim(), basic.financialYear(), basic.designationId(),
                excludeId).ifPresent(existing -> {
            throw DomainException.conflict(
                    "A template named '" + basic.name().trim() + "' already exists for this financial year "
                            + "and designation.", "PMS_TEMPLATE_DUPLICATE");
        });
    }

    /** Strict validation — only on activation (create/update with a non-draft
     * status, or the explicit active transition): ≥1 KRA each with ≥1 KPI,
     * weights summing to 100 across the whole template; ≥1 competency, weights
     * summing to 100; the designation must belong to the department. */
    private void assertComplete(String organisationId, GoalTemplateDtos.UpsertRequest request) {
        if (request.kras().isEmpty()) {
            throw DomainException.conflict("A goal template needs at least one KRA.", "PMS_TEMPLATE_INCOMPLETE");
        }
        Set<UUID> seenKras = new HashSet<>();
        BigDecimal kpiWeightTotal = BigDecimal.ZERO;
        for (PmsGoalTemplateKraDto kra : request.kras()) {
            if (!seenKras.add(kra.kraId())) {
                throw DomainException.unprocessable("Each KRA may only be added once.", "VALIDATION_ERROR");
            }
            if (kra.kpis().isEmpty()) {
                throw DomainException.conflict(
                        "Every KRA needs at least one KPI.", "PMS_TEMPLATE_INCOMPLETE");
            }
            Set<UUID> seenKpis = new HashSet<>();
            for (PmsGoalTemplateKpiDto kpi : kra.kpis()) {
                if (!seenKpis.add(kpi.kpiId())) {
                    throw DomainException.unprocessable(
                            "Each KPI may only be added once per KRA.", "VALIDATION_ERROR");
                }
                kpiWeightTotal = kpiWeightTotal.add(kpi.weight());
            }
        }
        if (kpiWeightTotal.subtract(WEIGHT_TOTAL).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            throw DomainException.conflict(
                    "KPI weights must add up to 100 (got " + kpiWeightTotal + ").", "PMS_TEMPLATE_INCOMPLETE");
        }

        if (request.competencies().isEmpty()) {
            throw DomainException.conflict(
                    "A goal template needs at least one competency.", "PMS_TEMPLATE_INCOMPLETE");
        }
        Set<UUID> seenCompetencies = new HashSet<>();
        BigDecimal competencyWeightTotal = BigDecimal.ZERO;
        for (PmsGoalTemplateCompetencyDto competency : request.competencies()) {
            if (!seenCompetencies.add(competency.competencyId())) {
                throw DomainException.unprocessable(
                        "Each competency may only be added once.", "VALIDATION_ERROR");
            }
            competencyWeightTotal = competencyWeightTotal.add(competency.weight());
        }
        if (competencyWeightTotal.subtract(WEIGHT_TOTAL).abs().compareTo(WEIGHT_TOLERANCE) > 0) {
            throw DomainException.conflict(
                    "Competency weights must add up to 100 (got " + competencyWeightTotal + ").",
                    "PMS_TEMPLATE_INCOMPLETE");
        }

        designations.findById(request.basic().designationId())
                .filter(d -> organisationId.equals(d.getOrganisationId()))
                .filter(d -> request.basic().departmentId().equals(d.getDepartmentId()))
                .orElseThrow(() -> DomainException.unprocessable(
                        "designation_id does not belong to department_id", "VALIDATION_ERROR"));
    }

    /** Resolves every referenced KRA/KPI/competency within the caller's organisation, then replaces the children. */
    private void applyChildren(String organisationId, PmsGoalTemplateEntity template,
                               GoalTemplateDtos.UpsertRequest request) {
        List<PmsGoalTemplateKraEntity> kraRows = new ArrayList<>();
        for (PmsGoalTemplateKraDto entry : request.kras()) {
            PmsKraEntity kra = kras.findByIdAndOrganisationId(entry.kraId(), organisationId)
                    .orElseThrow(() -> DomainException.unprocessable(
                            "kra_id " + entry.kraId() + " does not refer to an existing KRA", "VALIDATION_ERROR"));
            PmsGoalTemplateKraEntity kraRow = PmsGoalTemplateKraEntity.builder().kra(kra).build();
            for (PmsGoalTemplateKpiDto kpiEntry : entry.kpis()) {
                PmsKpiEntity kpi = kpis.findByIdAndOrganisationId(kpiEntry.kpiId(), organisationId)
                        .orElseThrow(() -> DomainException.unprocessable(
                                "kpi_id " + kpiEntry.kpiId() + " does not refer to an existing KPI",
                                "VALIDATION_ERROR"));
                PmsTargetType override = kpiEntry.targetType() != null && !kpiEntry.targetType().isBlank()
                        ? PmsTargetType.fromWire(kpiEntry.targetType()) : null;
                kraRow.addKpi(PmsGoalTemplateKpiEntity.builder()
                        .kpi(kpi)
                        .weight(kpiEntry.weight())
                        .targetType(override)
                        .expectedOutcome(kpiEntry.expectedOutcome())
                        .evidenceRequired(kpiEntry.evidenceRequired())
                        .build());
            }
            kraRows.add(kraRow);
        }
        template.replaceKras(kraRows);

        List<PmsGoalTemplateCompetencyEntity> competencyRows = new ArrayList<>();
        for (PmsGoalTemplateCompetencyDto entry : request.competencies()) {
            PmsCompetencyEntity competency = competencies
                    .findByIdAndOrganisationId(entry.competencyId(), organisationId)
                    .orElseThrow(() -> DomainException.unprocessable(
                            "competency_id " + entry.competencyId() + " does not refer to an existing competency",
                            "VALIDATION_ERROR"));
            competencyRows.add(PmsGoalTemplateCompetencyEntity.builder()
                    .competency(competency)
                    .weight(entry.weight())
                    .build());
        }
        template.replaceCompetencies(competencyRows);
    }

    private GoalTemplateDtos.Basic toBasic(PmsGoalTemplateDto dto) {
        return new GoalTemplateDtos.Basic(dto.financialYear(), dto.name(), dto.description(), dto.departmentId(),
                dto.designationId(), dto.effectiveFrom(), dto.status());
    }

    private GoalTemplateDtos.UpsertRequest toUpsertRequest(PmsGoalTemplateEntity template) {
        PmsGoalTemplateDto dto = mapper.toDto(template);
        return new GoalTemplateDtos.UpsertRequest(toBasic(dto), dto.kras(), dto.competencies());
    }

    private GoalTemplateDto toDto(PmsGoalTemplateEntity template) {
        PmsGoalTemplateDto dto = mapper.toDto(template);
        String departmentName = departments.findById(template.getDepartmentId()).map(d -> d.getName()).orElse(null);
        String designationName = designations.findById(template.getDesignationId()).map(d -> d.getName()).orElse(null);
        // IAM designations carry no plant/business-unit reference, so "the plant
        // this role belongs to" (per the contract's derivation note) has no
        // source of truth to derive from — see the PMS Configuration contract's
        // own open question #1. Reporting "All" rather than fabricating a value.
        return new GoalTemplateDto(dto.id(), departmentName, designationName, "All", toBasic(dto), dto.kras(),
                dto.competencies());
    }

    private GoalTemplateDtos.Summary toSummary(PmsGoalTemplateEntity template) {
        String departmentName = departments.findById(template.getDepartmentId()).map(d -> d.getName()).orElse(null);
        String designationName = designations.findById(template.getDesignationId()).map(d -> d.getName()).orElse(null);
        return new GoalTemplateDtos.Summary(template.getId(), template.getName(), template.getFinancialYear(),
                designationName, template.getDepartmentId(), departmentName, null, "All",
                template.getStatus().wire());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
