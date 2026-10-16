package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsGoalProgressEntity;
import com.sentrifugo.db.entity.PmsMidYearReviewEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionRowEntity;
import com.sentrifugo.db.enums.PmsAssignmentStatus;
import com.sentrifugo.db.enums.PmsMidYearStatus;
import com.sentrifugo.db.enums.PmsProgressPeriod;
import com.sentrifugo.db.enums.PmsRevisionStatus;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsMidYearReviewRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRowRepository;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.integration.model.IamEmployee;
import com.sentrifugo.pms.model.PmsMidYearReviewResponse;
import com.sentrifugo.pms.model.PmsMidYearRowResponse;
import com.sentrifugo.pms.model.PmsMidYearSubmitRequest;
import com.sentrifugo.pms.model.PmsRevisionRequestResponse;
import com.sentrifugo.pms.model.PmsRevisionSubmitRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The manager's mid-year review of the team (screens 3.10 - 3.12). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsMidYearService {

    private static final int MIN_NOTES = 10;
    private static final int MIN_REASON = 5;

    private final IamEmployeeClient iam;
    private final PmsTeamAccess access;
    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsMidYearReviewRepository reviewRepository;
    private final PmsTargetRevisionRepository revisionRepository;
    private final PmsTargetRevisionRowRepository revisionRowRepository;
    private final PmsSheetHistory sheetHistory;

    /** Screen 3.10: the team members whose goals are approved, with H1 achievement and review status. */
    @Transactional(readOnly = true)
    public List<PmsMidYearRowResponse> team(PmsUserPrincipal manager, String financialYear) {
        List<IamEmployee> reports = iam.directReports(manager.organisationId(), manager.id());
        if (reports.isEmpty()) {
            return List.of();
        }
        Map<String, IamEmployee> employeeById = new HashMap<>();
        reports.forEach(e -> employeeById.put(e.userId(), e));
        List<PmsGoalAssignmentEntity> approved = assignmentRepository
                .findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(manager.organisationId(), financialYear,
                        reports.stream().map(IamEmployee::userId).toList())
                .stream()
                .filter(a -> a.getStatus() == PmsAssignmentStatus.APPROVED)
                .toList();
        List<UUID> ids = approved.stream().map(PmsGoalAssignmentEntity::getId).toList();
        Map<UUID, Map<UUID, PmsGoalProgressEntity>> readings =
                access.readings(ids, PmsProgressPeriod.H1, PmsProgressPeriod.CURRENT);
        Map<UUID, PmsMidYearStatus> statusByAssignment = new HashMap<>();
        if (!ids.isEmpty()) {
            reviewRepository.findByAssignmentIdIn(ids)
                    .forEach(r -> statusByAssignment.put(r.getAssignment().getId(), r.getStatus()));
        }

        List<PmsMidYearRowResponse> rows = new ArrayList<>();
        for (PmsGoalAssignmentEntity a : approved) {
            IamEmployee e = employeeById.get(a.getEmployeeUserId());
            BigDecimal h1 = achievement(a, readings.getOrDefault(a.getId(), Map.of()));
            rows.add(new PmsMidYearRowResponse(a.getEmployeeUserId(), PmsTeamAccess.fullName(e),
                    e.designationName(), h1, PmsAchievement.rag(h1),
                    PmsTeamAccess.wire(statusByAssignment.getOrDefault(a.getId(), PmsMidYearStatus.NOT_STARTED))));
        }
        return rows;
    }

    /** Screen 3.11: H1 actual against each target, plus the saved notes and decision. */
    @Transactional(readOnly = true)
    public PmsMidYearReviewResponse review(PmsUserPrincipal manager, String employeeUserId, String financialYear) {
        IamEmployee employee = access.requireTeamMember(manager, employeeUserId);
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), employeeUserId, financialYear);
        Map<UUID, PmsGoalProgressEntity> readings = access
                .readings(List.of(a.getId()), PmsProgressPeriod.H1, PmsProgressPeriod.CURRENT)
                .getOrDefault(a.getId(), Map.of());
        List<PmsMidYearReviewResponse.Kra> kras = new ArrayList<>();
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(a)) {
            PmsGoalProgressEntity reading = readings.get(t.getKpi().getId());
            BigDecimal actual = reading == null ? null : reading.getActual();
            kras.add(new PmsMidYearReviewResponse.Kra(t.getKpi().getId(), t.getKpi().getName(), t.getTarget(),
                    t.getKpi().getUnit(), actual, PmsAchievement.rag(PmsAchievement.percent(actual, t.getTarget()))));
        }
        Optional<PmsMidYearReviewEntity> review = reviewRepository.findByAssignmentId(a.getId());
        return new PmsMidYearReviewResponse(employeeUserId, PmsTeamAccess.fullName(employee), employee.empCode(),
                employee.designationName(), manager.fullName(), access.templateOf(a).getTemplateName(),
                PmsTeamAccess.wire(review.map(PmsMidYearReviewEntity::getStatus)
                        .orElse(PmsMidYearStatus.NOT_STARTED)),
                kras,
                review.map(PmsMidYearReviewEntity::getDiscussionNotes).orElse(""),
                review.map(PmsMidYearReviewEntity::getRevisionRequired).orElse(null));
    }

    /**
     * Screen 3.11 Continue. "No revision" closes the review. "Revision" keeps it open until the target
     * revision request is sent (screen 3.12), and needs notes that justify it.
     */
    @Transactional
    public PmsMidYearReviewResponse submit(PmsUserPrincipal manager, PmsMidYearSubmitRequest request) {
        access.requireTeamMember(manager, request.employeeUserId());
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), request.employeeUserId(),
                request.financialYear());
        PmsMidYearReviewEntity review = reviewFor(a);
        if (review.getStatus() == PmsMidYearStatus.REVISION_REQUESTED) {
            throw DomainException.conflict("A target revision is already with the HOD for this employee.",
                    "PMS_REVISION_PENDING");
        }
        String notes = request.discussionNotes() == null ? "" : request.discussionNotes().trim();
        if (Boolean.TRUE.equals(request.revisionRequired()) && notes.length() < MIN_NOTES) {
            throw DomainException.unprocessable("Add at least " + MIN_NOTES
                    + " characters of discussion notes to justify a target revision.", "PMS_NOTES_REQUIRED");
        }
        review.setDiscussionNotes(notes);
        review.setRevisionRequired(request.revisionRequired());
        review.setReviewedOn(LocalDateTime.now());
        review.setStatus(Boolean.TRUE.equals(request.revisionRequired())
                ? PmsMidYearStatus.NOT_STARTED : PmsMidYearStatus.COMPLETED_NO_REVISION);
        reviewRepository.save(review);
        log.info("Mid-year review of {} saved by {} (revision: {})", request.employeeUserId(), manager.id(),
                request.revisionRequired());
        return review(manager, request.employeeUserId(), request.financialYear());
    }

    /** Screen 3.12: every KPI with its locked weight; revised targets filled in once a request exists. */
    @Transactional(readOnly = true)
    public PmsRevisionRequestResponse revisionRequest(PmsUserPrincipal manager, String employeeUserId,
                                                      String financialYear) {
        IamEmployee employee = access.requireTeamMember(manager, employeeUserId);
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), employeeUserId, financialYear);
        Optional<PmsTargetRevisionEntity> revision = revisionRepository
                .findFirstByAssignmentIdOrderByRequestedOnDesc(a.getId());
        Map<UUID, PmsTargetRevisionRowEntity> revised = new HashMap<>();
        revision.ifPresent(r -> revisionRowRepository.findByRevisionId(r.getId())
                .forEach(row -> revised.put(row.getKpi().getId(), row)));

        List<PmsRevisionRequestResponse.Kra> kras = new ArrayList<>();
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(a)) {
            PmsTargetRevisionRowEntity row = revised.get(t.getKpi().getId());
            kras.add(new PmsRevisionRequestResponse.Kra(t.getKpi().getId(), t.getKpi().getName(), t.getWeight(),
                    row != null ? row.getPreviousTarget() : t.getTarget(), t.getKpi().getUnit(),
                    row == null ? null : row.getRevisedTarget(), row == null ? "" : row.getReason()));
        }
        return new PmsRevisionRequestResponse(employeeUserId, PmsTeamAccess.fullName(employee), employee.empCode(),
                employee.designationName(), manager.fullName(), access.templateOf(a).getTemplateName(), kras,
                revision.map(PmsTargetRevisionEntity::getAttachmentName).orElse(null),
                revision.map(r -> PmsTeamAccess.wire(r.getStatus())).orElse(null));
    }

    /**
     * Screen 3.12 Send to HOD. Only targets can change at mid-year: KRAs and weightage stay locked, so
     * the request carries just the KPIs whose target moved, each with a reason.
     */
    @Transactional
    public PmsRevisionRequestResponse submitRevision(PmsUserPrincipal manager, PmsRevisionSubmitRequest request) {
        access.requireTeamMember(manager, request.employeeUserId());
        PmsGoalAssignmentEntity a = access.requireApproved(manager.organisationId(), request.employeeUserId(),
                request.financialYear());
        PmsMidYearReviewEntity review = reviewFor(a);
        if (review.getStatus() == PmsMidYearStatus.REVISION_REQUESTED) {
            throw DomainException.conflict("A target revision is already with the HOD for this employee.",
                    "PMS_REVISION_PENDING");
        }
        Map<UUID, PmsGoalAssignmentTargetEntity> onSheet = new HashMap<>();
        access.targetsInOrder(a).forEach(t -> onSheet.put(t.getKpi().getId(), t));

        List<String> errors = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (PmsRevisionSubmitRequest.Row row : request.rows()) {
            PmsGoalAssignmentTargetEntity current = onSheet.get(row.kpiId());
            if (current == null) {
                errors.add("kpi_id " + row.kpiId() + " is not on this goal sheet");
                continue;
            }
            String name = current.getKpi().getName();
            if (!seen.add(row.kpiId())) {
                errors.add(name + " appears more than once");
            }
            if (row.revisedTarget().signum() < 0) {
                errors.add("Revised target for " + name + " cannot be negative");
            }
            if (current.getTarget() != null && current.getTarget().compareTo(row.revisedTarget()) == 0) {
                errors.add("Revised target for " + name + " is the same as the current target");
            }
            if (row.reason().trim().length() < MIN_REASON) {
                errors.add("Give a reason for " + name);
            }
        }
        if (!errors.isEmpty()) {
            throw DomainException.unprocessable(String.join("; ", errors), "PMS_REVISION_INVALID");
        }

        PmsTargetRevisionEntity revision = revisionRepository.save(PmsTargetRevisionEntity.builder()
                .assignment(a)
                .attachmentName(request.attachmentName())
                .status(PmsRevisionStatus.PENDING_HOD)
                .requestedBy(manager.id())
                .requestedOn(LocalDateTime.now())
                .build());
        for (PmsRevisionSubmitRequest.Row row : request.rows()) {
            PmsGoalAssignmentTargetEntity current = onSheet.get(row.kpiId());
            revisionRowRepository.save(PmsTargetRevisionRowEntity.builder()
                    .revision(revision)
                    .kpi(current.getKpi())
                    .previousTarget(current.getTarget())
                    .revisedTarget(row.revisedTarget())
                    .reason(row.reason().trim())
                    .build());
            sheetHistory.record(a, PmsSheetHistory.TARGET_REVISION, revision.getId(), "revision_requested",
                    manager.id(), manager.fullName(), PmsSheetHistory.MANAGER, current.getKpi().getId(),
                    current.getKpi().getName(), current.getKpi().getUnit(), current.getTarget(),
                    row.revisedTarget(), 0,
                    "Asked to change " + current.getKpi().getName() + " " + PmsSheetHistory.num(current.getTarget())
                            + " -> " + PmsSheetHistory.num(row.revisedTarget()) + ". Reason: "
                            + row.reason().trim(), row.reason().trim());
        }
        review.setRevisionRequired(true);
        review.setStatus(PmsMidYearStatus.REVISION_REQUESTED);
        review.setReviewedOn(LocalDateTime.now());
        reviewRepository.save(review);
        log.info("Target revision for {} sent to the HOD by {}", request.employeeUserId(), manager.id());
        return revisionRequest(manager, request.employeeUserId(), request.financialYear());
    }

    // ── internals ────────────────────────────────────────────────────────────

    private PmsMidYearReviewEntity reviewFor(PmsGoalAssignmentEntity assignment) {
        return reviewRepository.findByAssignmentId(assignment.getId())
                .orElseGet(() -> PmsMidYearReviewEntity.builder()
                        .assignment(assignment)
                        .status(PmsMidYearStatus.NOT_STARTED)
                        .build());
    }

    private BigDecimal achievement(PmsGoalAssignmentEntity assignment, Map<UUID, PmsGoalProgressEntity> readings) {
        List<PmsAchievement.Weighted> weighted = new ArrayList<>();
        for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(assignment)) {
            PmsGoalProgressEntity reading = readings.get(t.getKpi().getId());
            weighted.add(new PmsAchievement.Weighted(t.getWeight(),
                    reading == null ? null : PmsAchievement.percent(reading.getActual(), t.getTarget())));
        }
        return PmsAchievement.weightedAverage(weighted);
    }
}
