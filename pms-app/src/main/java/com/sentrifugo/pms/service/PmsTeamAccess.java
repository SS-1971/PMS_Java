package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsGoalProgressEntity;
import com.sentrifugo.db.entity.PmsGoalTemplateEntity;
import com.sentrifugo.db.enums.PmsAssignmentStatus;
import com.sentrifugo.db.enums.PmsProgressPeriod;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentTargetRepository;
import com.sentrifugo.db.repository.PmsGoalProgressRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateKpiRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamEmployee;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What the manager screens after goal setting (progress, mid-year, appraisal) all need first: is this
 * employee in the caller's team, and which approved goal sheet and progress readings do they have.
 */
@Component
@RequiredArgsConstructor
public class PmsTeamAccess {

    private final IamEmployeeClient iam;
    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsGoalAssignmentTargetRepository targetRepository;
    private final PmsGoalTemplateRepository templateRepository;
    private final PmsGoalTemplateKpiRepository templateKpiRepository;
    private final PmsGoalProgressRepository progressRepository;

    /** Only the caller's own direct reports may be read or changed. */
    public IamEmployee requireTeamMember(PmsUserPrincipal manager, String employeeUserId) {
        return iam.directReports(manager.organisationId(), manager.id()).stream()
                .filter(e -> employeeUserId.equals(e.userId()))
                .findFirst()
                .orElseThrow(() -> new DomainException("This employee is not in your team.",
                        "PMS_NOT_IN_TEAM", HttpStatus.FORBIDDEN));
    }

    public PmsGoalAssignmentEntity requireAssignment(String organisationId, String employeeUserId,
                                                     String financialYear) {
        return assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(organisationId, employeeUserId, financialYear)
                .orElseThrow(() -> DomainException.notFound("No goals have been set for " + financialYear,
                        "PMS_GOALS_NOT_FOUND"));
    }

    /** The goal sheet the manager screens work on: it has to be HOD approved. */
    public PmsGoalAssignmentEntity requireApproved(String organisationId, String employeeUserId,
                                                   String financialYear) {
        PmsGoalAssignmentEntity a = requireAssignment(organisationId, employeeUserId, financialYear);
        if (a.getStatus() != PmsAssignmentStatus.APPROVED) {
            throw DomainException.conflict("The HOD has not approved these goals yet.", "PMS_GOALS_NOT_APPROVED");
        }
        return a;
    }

    public PmsGoalTemplateEntity templateOf(PmsGoalAssignmentEntity assignment) {
        return templateRepository.findById(assignment.getTemplateId())
                .orElseThrow(() -> DomainException.notFound("Goal template not found", "PMS_TEMPLATE_NOT_FOUND"));
    }

    /** The sheet's targets in template order, so every screen lists the KPIs the same way. */
    public List<PmsGoalAssignmentTargetEntity> targetsInOrder(PmsGoalAssignmentEntity assignment) {
        Map<UUID, Integer> order = new HashMap<>();
        templateKpiRepository.findByTemplateIdOrderByDisplayOrderAsc(assignment.getTemplateId())
                .forEach(row -> order.put(row.getKpi().getId(), order.size()));
        return targetRepository.findByAssignmentId(assignment.getId()).stream()
                .sorted(Comparator.comparingInt(t -> order.getOrDefault(t.getKpi().getId(), Integer.MAX_VALUE)))
                .toList();
    }

    /**
     * The reading to use per KPI for each assignment: the first period in {@code preference} that has an
     * actual, so a half-year figure wins over a running one for the mid-year review.
     */
    public Map<UUID, Map<UUID, PmsGoalProgressEntity>> readings(Collection<UUID> assignmentIds,
                                                                PmsProgressPeriod... preference) {
        Map<UUID, Map<UUID, PmsGoalProgressEntity>> out = new HashMap<>();
        if (assignmentIds.isEmpty()) {
            return out;
        }
        List<PmsProgressPeriod> order = List.of(preference);
        for (PmsGoalProgressEntity p : progressRepository.findByAssignmentIdIn(assignmentIds)) {
            if (p.getActual() == null || !order.contains(p.getPeriod())) {
                continue;
            }
            Map<UUID, PmsGoalProgressEntity> byKpi =
                    out.computeIfAbsent(p.getAssignment().getId(), k -> new HashMap<>());
            PmsGoalProgressEntity current = byKpi.get(p.getKpi().getId());
            if (current == null || order.indexOf(p.getPeriod()) < order.indexOf(current.getPeriod())) {
                byKpi.put(p.getKpi().getId(), p);
            }
        }
        return out;
    }

    public static String fullName(IamEmployee e) {
        return ((e.firstName() == null ? "" : e.firstName()) + " "
                + (e.lastName() == null ? "" : e.lastName())).trim();
    }

    public static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
