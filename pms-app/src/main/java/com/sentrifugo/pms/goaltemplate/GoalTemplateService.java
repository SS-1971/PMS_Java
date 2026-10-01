package com.sentrifugo.pms.goaltemplate;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.config.Competency;
import com.sentrifugo.db.config.CompetencyRepository;
import com.sentrifugo.db.config.GoalTemplate;
import com.sentrifugo.db.config.GoalTemplateCompetency;
import com.sentrifugo.db.config.GoalTemplateKpi;
import com.sentrifugo.db.config.GoalTemplateKra;
import com.sentrifugo.db.config.GoalTemplateRepository;
import com.sentrifugo.db.config.Kpi;
import com.sentrifugo.db.config.KpiRepository;
import com.sentrifugo.db.config.Kra;
import com.sentrifugo.db.config.KraRepository;
import com.sentrifugo.db.config.TargetType;
import com.sentrifugo.db.config.TemplateStatus;
import com.sentrifugo.db.replica.DepartmentReplicaRepository;
import com.sentrifugo.db.replica.DesignationReplicaRepository;
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

    private final GoalTemplateRepository templates;
    private final KraRepository kras;
    private final KpiRepository kpis;
    private final CompetencyRepository competencies;
    private final DepartmentReplicaRepository departments;
    private final DesignationReplicaRepository designations;

    public GoalTemplateService(GoalTemplateRepository templates, KraRepository kras, KpiRepository kpis,
                               CompetencyRepository competencies, DepartmentReplicaRepository departments,
                               DesignationReplicaRepository designations) {
        this.templates = templates;
        this.kras = kras;
        this.kpis = kpis;
        this.competencies = competencies;
        this.departments = departments;
        this.designations = designations;
    }

    public List<GoalTemplateDtos.Summary> list(String organisationId, Integer financialYear, String departmentId,
                                               String search) {
        return templates.search(organisationId, financialYear, departmentId, blankToNull(search)).stream()
                .map(this::toSummary).toList();
    }

    public GoalTemplateDto get(String organisationId, UUID templateId) {
        return toDto(find(organisationId, templateId));
    }

    @Transactional
    public GoalTemplateDto create(String organisationId, GoalTemplateDtos.UpsertRequest request) {
        TemplateStatus status = TemplateStatus.fromWire(request.basic().status());
        assertNameFree(organisationId, request.basic(), status, null);
        if (status != TemplateStatus.DRAFT) {
            assertComplete(organisationId, request);
        }

        GoalTemplate template = new GoalTemplate(organisationId, request.basic().financialYear(),
                request.basic().name().trim(), request.basic().description(), request.basic().departmentId(),
                request.basic().designationId(), request.basic().effectiveFrom(), status);
        applyChildren(organisationId, template, request);
        return toDto(templates.save(template));
    }

    @Transactional
    public GoalTemplateDto update(String organisationId, UUID templateId, GoalTemplateDtos.UpsertRequest request) {
        GoalTemplate template = find(organisationId, templateId);
        TemplateStatus status = TemplateStatus.fromWire(request.basic().status());
        assertNameFree(organisationId, request.basic(), status, templateId);
        if (status != TemplateStatus.DRAFT) {
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
        GoalTemplate template = find(organisationId, templateId);
        TemplateStatus status = TemplateStatus.fromWire(statusWire);
        if (status == TemplateStatus.ACTIVE) {
            assertComplete(organisationId, toUpsertRequest(template));
        }
        template.setStatus(status);
        return toDto(templates.save(template));
    }

    @Transactional
    public GoalTemplateDto duplicate(String organisationId, UUID templateId) {
        GoalTemplate source = find(organisationId, templateId);
        GoalTemplate copy = new GoalTemplate(organisationId, source.getFinancialYear(),
                source.getName() + " (Copy)", source.getDescription(), source.getDepartmentId(),
                source.getDesignationId(), source.getEffectiveFrom(), TemplateStatus.DRAFT);
        applyChildren(organisationId, copy, toUpsertRequest(source));
        return toDto(templates.save(copy));
    }

    @Transactional
    public void delete(String organisationId, UUID templateId, String actorId) {
        GoalTemplate template = find(organisationId, templateId);
        template.softDelete(actorId);
        templates.save(template);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private GoalTemplate find(String organisationId, UUID templateId) {
        return templates.findByIdAndOrganisationIdAndDeletedOnIsNull(templateId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Goal template not found", "PMS_TEMPLATE_NOT_FOUND"));
    }

    private void assertNameFree(String organisationId, GoalTemplateDtos.Basic basic, TemplateStatus status,
                                UUID excludeId) {
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
        for (GoalTemplateDtos.KraEntry kra : request.kras()) {
            if (!seenKras.add(kra.kraId())) {
                throw DomainException.unprocessable("Each KRA may only be added once.", "VALIDATION_ERROR");
            }
            if (kra.kpis().isEmpty()) {
                throw DomainException.conflict(
                        "Every KRA needs at least one KPI.", "PMS_TEMPLATE_INCOMPLETE");
            }
            Set<UUID> seenKpis = new HashSet<>();
            for (GoalTemplateDtos.KpiEntry kpi : kra.kpis()) {
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
        for (GoalTemplateDtos.CompetencyEntry competency : request.competencies()) {
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

    private void applyChildren(String organisationId, GoalTemplate template, GoalTemplateDtos.UpsertRequest request) {
        List<GoalTemplateKra> kraRows = new ArrayList<>();
        for (GoalTemplateDtos.KraEntry entry : request.kras()) {
            Kra kra = kras.findByIdAndOrganisationIdAndDeletedOnIsNull(entry.kraId(), organisationId)
                    .orElseThrow(() -> DomainException.unprocessable(
                            "kra_id " + entry.kraId() + " does not refer to an existing KRA", "VALIDATION_ERROR"));
            GoalTemplateKra kraRow = new GoalTemplateKra(kra);
            for (GoalTemplateDtos.KpiEntry kpiEntry : entry.kpis()) {
                Kpi kpi = kpis.findByIdAndOrganisationIdAndDeletedOnIsNull(kpiEntry.kpiId(), organisationId)
                        .orElseThrow(() -> DomainException.unprocessable(
                                "kpi_id " + kpiEntry.kpiId() + " does not refer to an existing KPI",
                                "VALIDATION_ERROR"));
                TargetType override = kpiEntry.targetType() != null && !kpiEntry.targetType().isBlank()
                        ? TargetType.fromWire(kpiEntry.targetType()) : null;
                kraRow.addKpi(new GoalTemplateKpi(kpi, kpiEntry.weight(), override, kpiEntry.expectedOutcome(),
                        kpiEntry.evidenceRequired()));
            }
            kraRows.add(kraRow);
        }
        template.replaceKras(kraRows);

        List<GoalTemplateCompetency> competencyRows = new ArrayList<>();
        for (GoalTemplateDtos.CompetencyEntry entry : request.competencies()) {
            Competency competency = competencies
                    .findByIdAndOrganisationIdAndDeletedOnIsNull(entry.competencyId(), organisationId)
                    .orElseThrow(() -> DomainException.unprocessable(
                            "competency_id " + entry.competencyId() + " does not refer to an existing competency",
                            "VALIDATION_ERROR"));
            competencyRows.add(new GoalTemplateCompetency(competency, entry.weight()));
        }
        template.replaceCompetencies(competencyRows);
    }

    private GoalTemplateDtos.UpsertRequest toUpsertRequest(GoalTemplate template) {
        List<GoalTemplateDtos.KraEntry> kraEntries = template.getKras().stream()
                .map(kraRow -> new GoalTemplateDtos.KraEntry(kraRow.getKra().getId(),
                        kraRow.getKpis().stream()
                                .map(k -> new GoalTemplateDtos.KpiEntry(k.getKpi().getId(), k.getWeight(),
                                        k.getTargetType() != null ? k.getTargetType().wire() : null,
                                        k.getExpectedOutcome(), k.getEvidenceRequired()))
                                .toList()))
                .toList();
        List<GoalTemplateDtos.CompetencyEntry> competencyEntries = template.getCompetencies().stream()
                .map(c -> new GoalTemplateDtos.CompetencyEntry(c.getCompetency().getId(), c.getWeight()))
                .toList();
        GoalTemplateDtos.Basic basic = new GoalTemplateDtos.Basic(template.getFinancialYear(), template.getName(),
                template.getDescription(), template.getDepartmentId(), template.getDesignationId(),
                template.getEffectiveFrom(), template.getStatus().wire());
        return new GoalTemplateDtos.UpsertRequest(basic, kraEntries, competencyEntries);
    }

    private GoalTemplateDto toDto(GoalTemplate template) {
        GoalTemplateDtos.Basic basic = new GoalTemplateDtos.Basic(template.getFinancialYear(), template.getName(),
                template.getDescription(), template.getDepartmentId(), template.getDesignationId(),
                template.getEffectiveFrom(), template.getStatus().wire());
        List<GoalTemplateDtos.KraEntry> kraEntries = template.getKras().stream()
                .map(kraRow -> new GoalTemplateDtos.KraEntry(kraRow.getKra().getId(),
                        kraRow.getKpis().stream()
                                .map(k -> new GoalTemplateDtos.KpiEntry(k.getKpi().getId(), k.getWeight(),
                                        k.getTargetType() != null ? k.getTargetType().wire() : null,
                                        k.getExpectedOutcome(), k.getEvidenceRequired()))
                                .toList()))
                .toList();
        List<GoalTemplateDtos.CompetencyEntry> competencyEntries = template.getCompetencies().stream()
                .map(c -> new GoalTemplateDtos.CompetencyEntry(c.getCompetency().getId(), c.getWeight()))
                .toList();
        String departmentName = departments.findById(template.getDepartmentId()).map(d -> d.getName()).orElse(null);
        String designationName = designations.findById(template.getDesignationId()).map(d -> d.getName()).orElse(null);
        // IAM designations carry no plant/business-unit reference, so "the plant
        // this role belongs to" (per the contract's derivation note) has no
        // source of truth to derive from — see the PMS Configuration contract's
        // own open question #1. Reporting "All" rather than fabricating a value.
        return new GoalTemplateDto(template.getId(), departmentName, designationName, "All", basic, kraEntries,
                competencyEntries);
    }

    private GoalTemplateDtos.Summary toSummary(GoalTemplate template) {
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
