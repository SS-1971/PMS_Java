package com.sentrifugo.pms.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsGoalProgressEntity;
import com.sentrifugo.db.enums.PmsAssignmentStatus;
import com.sentrifugo.db.enums.PmsProgressPeriod;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsGoalProgressRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamEmployee;
import com.sentrifugo.pms.model.PmsMyProgressResponse;
import com.sentrifugo.pms.model.PmsProgressUpdateRequest;
import com.sentrifugo.pms.model.PmsTeamProgressResponse;
import com.sentrifugo.pms.utils.PmsAchievement;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Team progress dashboard (screen 3.9) and the employee's entry of actuals that feeds it. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsProgressService {

    private final IamEmployeeClient iam;
    private final PmsTeamAccess access;
    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsGoalProgressRepository progressRepository;

    /** What an update saved. */
    public record Saved(
            @JsonProperty("financial_year") String financialYear,
            @JsonProperty("period") String period,
            @JsonProperty("updated") int updated) {
    }

    /**
     * Screen 3.9: every direct report's KRAs on track, those needing attention, and overall achievement.
     * Progress tracking opens once the HOD has approved the goals; until then the row is locked.
     */
    @Transactional(readOnly = true)
    public List<PmsTeamProgressResponse> teamProgress(PmsUserPrincipal manager, String financialYear) {
        List<IamEmployee> reports = iam.directReports(manager.organisationId(), manager.id());
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<String, PmsGoalAssignmentEntity> byEmployee = new HashMap<>();
        for (PmsGoalAssignmentEntity a : assignmentRepository
                .findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(manager.organisationId(), financialYear,
                        reports.stream().map(IamEmployee::userId).toList())) {
            byEmployee.put(a.getEmployeeUserId(), a);
        }
        List<UUID> approvedIds = byEmployee.values().stream()
                .filter(a -> a.getStatus() == PmsAssignmentStatus.APPROVED)
                .map(PmsGoalAssignmentEntity::getId)
                .toList();
        Map<UUID, Map<UUID, PmsGoalProgressEntity>> readings = access.readings(approvedIds,
                PmsProgressPeriod.CURRENT, PmsProgressPeriod.H1, PmsProgressPeriod.FY);

        List<PmsTeamProgressResponse> rows = new ArrayList<>();
        for (IamEmployee e : reports) {
            PmsGoalAssignmentEntity a = byEmployee.get(e.userId());
            if (a == null || a.getStatus() != PmsAssignmentStatus.APPROVED) {
                rows.add(new PmsTeamProgressResponse(e.userId(), PmsTeamAccess.fullName(e), true, 0, 0, List.of(),
                        null, null, null));
                continue;
            }
            rows.add(progressRow(e, a, readings.getOrDefault(a.getId(), Map.of())));
        }
        return rows;
    }

    private PmsTeamProgressResponse progressRow(IamEmployee employee, PmsGoalAssignmentEntity assignment,
                                                Map<UUID, PmsGoalProgressEntity> readings) {
        List<PmsGoalAssignmentTargetEntity> targets = access.targetsInOrder(assignment);
        int onTrack = 0;
        List<PmsTeamProgressResponse.Attention> attention = new ArrayList<>();
        List<PmsAchievement.Weighted> weighted = new ArrayList<>();
        String comment = null;
        for (PmsGoalAssignmentTargetEntity t : targets) {
            PmsGoalProgressEntity reading = readings.get(t.getKpi().getId());
            BigDecimal pct = reading == null ? null : PmsAchievement.percent(reading.getActual(), t.getTarget());
            String rag = PmsAchievement.rag(pct);
            weighted.add(new PmsAchievement.Weighted(t.getWeight(), pct));
            if (PmsAchievement.ON_TRACK.equals(rag)) {
                onTrack++;
            } else if (rag != null) {
                attention.add(new PmsTeamProgressResponse.Attention(t.getKpi().getName(), rag));
            }
            if (comment == null && reading != null && reading.getComment() != null
                    && !reading.getComment().isBlank()) {
                comment = reading.getComment();
            }
        }
        BigDecimal achievement = PmsAchievement.weightedAverage(weighted);
        return new PmsTeamProgressResponse(employee.userId(), PmsTeamAccess.fullName(employee), false, onTrack,
                targets.size(), attention, achievement, comment, PmsAchievement.rag(achievement));
    }

    /**
     * Screen 4.4: the signed-in employee progress sheet for one period. The Target column is the original
     * target; the Revised target column carries the final target after a revision. Needs HOD-approved goals.
     */
    @Transactional(readOnly = true)
    public PmsMyProgressResponse myProgress(PmsUserPrincipal employee, String financialYear, String periodCode) {
        PmsProgressPeriod period = parsePeriod(periodCode);
        PmsGoalAssignmentEntity assignment = access.requireApproved(employee.organisationId(), employee.id(),
                financialYear);
        Map<UUID, PmsGoalProgressEntity> rows = new HashMap<>();
        for (PmsGoalProgressEntity p : progressRepository.findByAssignmentId(assignment.getId())) {
            if (p.getPeriod() == period) {
                rows.put(p.getKpi().getId(), p);
            }
        }
        List<PmsMyProgressResponse.Kra> kras = new ArrayList<>();
        String comment = "";
        LocalDateTime submittedOn = null;
        boolean frozen = false;
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(assignment)) {
            PmsGoalProgressEntity reading = rows.get(t.getKpi().getId());
            BigDecimal original = t.getOriginalTarget() != null ? t.getOriginalTarget() : t.getTarget();
            BigDecimal revised = t.getTarget() != null && original != null && t.getTarget().compareTo(original) != 0
                    ? t.getTarget() : null;
            if (reading != null) {
                if (reading.getComment() != null && comment.isEmpty()) {
                    comment = reading.getComment();
                }
                if (reading.getSubmittedOn() != null) {
                    frozen = true;
                    submittedOn = reading.getSubmittedOn();
                }
            }
            kras.add(new PmsMyProgressResponse.Kra(t.getKpi().getId(),
                    t.getKpi().getKra() == null ? null : t.getKpi().getKra().getName(), t.getKpi().getName(),
                    t.getKpi().getUnit(), t.getWeight(), original, revised,
                    reading == null ? null : reading.getActual(), reading == null ? null : reading.getStatus(),
                    reading == null ? null : reading.getAttachmentName()));
        }
        return new PmsMyProgressResponse(financialYear, period.name().toLowerCase(Locale.ROOT),
                frozen ? "frozen" : "open", submittedOn, comment, kras);
    }

    /**
     * The signed-in employee reports actuals against their approved goals. A KPI that is not on the
     * goal sheet is refused; reporting the same KPI and period again replaces the figure. A submitted
     * period is frozen. Submitting needs an actual and a status for every KPI; the manager only views
     * the result, there is no approval.
     */
    @Transactional
    public Saved updateMyProgress(PmsUserPrincipal employee, PmsProgressUpdateRequest request) {
        PmsProgressPeriod period = parsePeriod(request.period());
        PmsGoalAssignmentEntity assignment = access.requireApproved(employee.organisationId(), employee.id(),
                request.financialYear());
        Map<UUID, PmsGoalAssignmentTargetEntity> onSheet = new HashMap<>();
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(assignment)) {
            onSheet.put(t.getKpi().getId(), t);
        }
        Map<UUID, PmsGoalProgressEntity> existing = new HashMap<>();
        for (PmsGoalProgressEntity p : progressRepository.findByAssignmentId(assignment.getId())) {
            if (p.getPeriod() == period) {
                existing.put(p.getKpi().getId(), p);
            }
        }
        if (existing.values().stream().anyMatch(p -> p.getSubmittedOn() != null)) {
            throw DomainException.conflict("This period was already submitted to your manager.",
                    "PMS_PROGRESS_FROZEN");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        Map<UUID, PmsGoalProgressEntity> touched = new HashMap<>(existing);
        for (PmsProgressUpdateRequest.Entry entry : request.entries()) {
            PmsGoalAssignmentTargetEntity target = onSheet.get(entry.kpiId());
            if (target == null) {
                throw DomainException.unprocessable("kpi_id " + entry.kpiId() + " is not on your goal sheet",
                        "PMS_KPI_NOT_IN_TEMPLATE");
            }
            if (entry.actual().signum() < 0) {
                throw DomainException.unprocessable("Actual for " + target.getKpi().getName()
                        + " cannot be negative", "VALIDATION_ERROR");
            }
            PmsGoalProgressEntity row = existing.getOrDefault(entry.kpiId(), PmsGoalProgressEntity.builder()
                    .assignment(assignment)
                    .kpi(target.getKpi())
                    .period(period)
                    .build());
            row.setActual(entry.actual());
            row.setComment(comment);
            row.setAttachmentName(entry.attachmentName());
            row.setStatus(entry.status());
            touched.put(entry.kpiId(), progressRepository.save(row));
        }
        boolean submit = Boolean.TRUE.equals(request.submit());
        if (submit) {
            List<String> missing = new ArrayList<>();
            for (PmsGoalAssignmentTargetEntity t : onSheet.values()) {
                PmsGoalProgressEntity row = touched.get(t.getKpi().getId());
                if (row == null || row.getActual() == null || row.getStatus() == null) {
                    missing.add(t.getKpi().getName());
                }
            }
            if (!missing.isEmpty()) {
                throw DomainException.unprocessable("Enter the actual and status for: " + String.join(", ", missing),
                        "PMS_PROGRESS_INCOMPLETE");
            }
            LocalDateTime now = LocalDateTime.now();
            for (PmsGoalProgressEntity row : touched.values()) {
                row.setSubmittedOn(now);
                row.setComment(comment);
                progressRepository.save(row);
            }
        }
        log.info("Employee {} {} {} actuals for {} ({})", employee.id(), submit ? "submitted" : "saved",
                request.entries().size(), request.financialYear(), period);
        return new Saved(request.financialYear(), period.name().toLowerCase(Locale.ROOT), request.entries().size());
    }

    private static PmsProgressPeriod parsePeriod(String value) {
        try {
            return PmsProgressPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw DomainException.unprocessable("period must be one of: current, h1, fy", "VALIDATION_ERROR");
        }
    }
}
