package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsGoalChangeRequestEntity;
import com.sentrifugo.db.entity.PmsGoalSheetHistoryEntity;
import com.sentrifugo.db.entity.PmsMidYearReviewEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionRowEntity;
import com.sentrifugo.db.enums.PmsAssignmentStatus;
import com.sentrifugo.db.enums.PmsMidYearStatus;
import com.sentrifugo.db.enums.PmsRevisionSource;
import com.sentrifugo.db.enums.PmsRevisionStatus;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsGoalChangeRequestRepository;
import com.sentrifugo.db.repository.PmsGoalSheetHistoryRepository;
import com.sentrifugo.db.repository.PmsMidYearReviewRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRowRepository;
import com.sentrifugo.pms.model.PmsApprovalActionRequest;
import com.sentrifugo.pms.model.PmsBulkApproveRequest;
import com.sentrifugo.pms.model.PmsBulkApproveResponse;
import com.sentrifugo.pms.model.PmsHodActionRequest;
import com.sentrifugo.pms.model.PmsHodHistoryItemResponse;
import com.sentrifugo.pms.model.PmsHodQueueItemResponse;
import com.sentrifugo.pms.model.PmsHodReviewResponse;
import com.sentrifugo.pms.model.PmsHodTrailResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The HOD side of goal setting: the approval list (goal sheets and mid-year target revisions), the review of
 * one request, approve / return (a whole sheet, or selected KPIs of a revision), bulk approve, and the
 * history and trail read back from the sheet history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsHodApprovalService {

    /** The HOD list shows only sheets the manager has sent to the HOD, not merely acknowledged ones. */
    private static final List<PmsAssignmentStatus> SHEET_QUEUE = List.of(PmsAssignmentStatus.WITH_HOD);

    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsGoalChangeRequestRepository changeRequestRepository;
    private final PmsTargetRevisionRepository revisionRepository;
    private final PmsTargetRevisionRowRepository revisionRowRepository;
    private final PmsMidYearReviewRepository midYearReviewRepository;
    private final PmsGoalSheetHistoryRepository historyRepository;
    private final PmsTeamAccess access;
    private final PmsGoalAssignmentService goalService;
    private final PmsSheetHistory sheetHistory;
    private final TransactionTemplate transactions;

    // ── list ─────────────────────────────────────────────────────────────────

    /**
     * Goal sheets sent to the HOD plus approved sheets that have a target revision waiting. Newest first.
     *
     * <p>Every row's targets, history and change-request counts are fetched in a handful of batched queries
     * (keyed by assignment / revision id) instead of the several per-row queries this used to run, so the
     * query count no longer scales with how many sheets are in the queue.
     */
    @Transactional(readOnly = true)
    public List<PmsHodQueueItemResponse> queue(PmsUserPrincipal hod, String financialYear) {
        List<PmsGoalAssignmentEntity> sheets = assignmentRepository.findByOrganisationIdAndFinancialYearAndStatusIn(
                hod.organisationId(), financialYear, SHEET_QUEUE);
        List<PmsGoalAssignmentEntity> approvedList = assignmentRepository
                .findByOrganisationIdAndFinancialYearAndStatusIn(hod.organisationId(), financialYear,
                        List.of(PmsAssignmentStatus.APPROVED));

        List<PmsGoalAssignmentEntity> all = new ArrayList<>(sheets);
        all.addAll(approvedList);
        List<UUID> allIds = all.stream().map(PmsGoalAssignmentEntity::getId).toList();

        Map<UUID, List<PmsGoalAssignmentTargetEntity>> targetsByAssignment = access.targetsInOrderBatch(all);
        Map<UUID, List<PmsGoalSheetHistoryEntity>> historyByAssignment = new HashMap<>();
        if (!allIds.isEmpty()) {
            historyRepository.findByAssignmentIdInOrderByOccurredOnAsc(allIds)
                    .forEach(h -> historyByAssignment
                            .computeIfAbsent(h.getAssignment().getId(), k -> new ArrayList<>()).add(h));
        }
        Map<UUID, Integer> changeRequestCountByAssignment = new HashMap<>();
        if (!allIds.isEmpty()) {
            changeRequestRepository.findByAssignmentIdIn(allIds)
                    .forEach(r -> changeRequestCountByAssignment.merge(r.getAssignment().getId(), 1, Integer::sum));
        }

        List<UUID> approvedIds = approvedList.stream().map(PmsGoalAssignmentEntity::getId).toList();
        Map<UUID, PmsTargetRevisionEntity> pendingRevisionByAssignment = new HashMap<>();
        if (!approvedIds.isEmpty()) {
            for (PmsTargetRevisionEntity r : revisionRepository.findByAssignmentIdInAndStatus(approvedIds,
                    PmsRevisionStatus.PENDING_HOD)) {
                PmsTargetRevisionEntity existing = pendingRevisionByAssignment.get(r.getAssignment().getId());
                if (existing == null || r.getRequestedOn().isAfter(existing.getRequestedOn())) {
                    pendingRevisionByAssignment.put(r.getAssignment().getId(), r);
                }
            }
        }
        List<UUID> revisionIds = pendingRevisionByAssignment.values().stream()
                .map(PmsTargetRevisionEntity::getId).toList();
        Map<UUID, List<PmsTargetRevisionRowEntity>> rowsByRevision = new HashMap<>();
        if (!revisionIds.isEmpty()) {
            revisionRowRepository.findByRevisionIdIn(revisionIds)
                    .forEach(row -> rowsByRevision.computeIfAbsent(row.getRevision().getId(), k -> new ArrayList<>())
                            .add(row));
        }

        List<PmsHodQueueItemResponse> out = new ArrayList<>();
        for (PmsGoalAssignmentEntity a : sheets) {
            out.add(sheetItem(a, targetsByAssignment.getOrDefault(a.getId(), List.of()),
                    historyByAssignment.getOrDefault(a.getId(), List.of()),
                    changeRequestCountByAssignment.getOrDefault(a.getId(), 0)));
        }
        for (PmsGoalAssignmentEntity a : approvedList) {
            PmsTargetRevisionEntity revision = pendingRevisionByAssignment.get(a.getId());
            if (revision != null) {
                out.add(revisionItem(a, revision, rowsByRevision.getOrDefault(revision.getId(), List.of()),
                        historyByAssignment.getOrDefault(a.getId(), List.of())));
            }
        }
        out.sort(Comparator.comparing(PmsHodQueueItemResponse::askedOn,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private PmsHodQueueItemResponse sheetItem(PmsGoalAssignmentEntity a) {
        return sheetItem(a, access.targetsInOrder(a), historyRepository.findByAssignmentIdOrderByOccurredOnAsc(a.getId()),
                changeRequestRepository.findByAssignmentIdOrderByRequestedOnAscRevisionNoAsc(a.getId()).size());
    }

    private PmsHodQueueItemResponse sheetItem(PmsGoalAssignmentEntity a, List<PmsGoalAssignmentTargetEntity> targets,
                                              List<PmsGoalSheetHistoryEntity> history, int changeRequestCount) {
        PmsGoalAssignmentTargetEntity first = null;
        int moved = 0;
        for (PmsGoalAssignmentTargetEntity t : targets) {
            if (isMoved(t)) {
                moved++;
                if (first == null) {
                    first = t;
                }
            }
        }
        LocalDate askedOn = history.stream()
                .filter(h -> "sent_to_hod".equals(h.getAction()))
                .map(h -> h.getOccurredOn().toLocalDate())
                .reduce((x, y) -> y)
                .orElse(a.getAcknowledgedOn() == null ? null : a.getAcknowledgedOn().toLocalDate());
        int rounds = changeRequestCount + returnedCount(history);
        return new PmsHodQueueItemResponse(a.getEmployeeUserId(), a.getEmployeeName(), a.getEmpCode(),
                a.getDesignationName(), a.getManagerName(), PmsSheetHistory.GOAL_SHEET,
                PmsTeamAccess.wire(a.getStatus()), moved > 0,
                first == null ? null : new PmsHodQueueItemResponse.Change(first.getKpi().getName(),
                        first.getKpi().getUnit(), first.getOriginalTarget(), first.getTarget()),
                Math.max(0, moved - 1), a.getManagerName(), PmsSheetHistory.MANAGER, askedOn, rounds);
    }

    private PmsHodQueueItemResponse revisionItem(PmsGoalAssignmentEntity a, PmsTargetRevisionEntity revision) {
        return revisionItem(a, revision, revisionRowRepository.findByRevisionId(revision.getId()),
                historyRepository.findByAssignmentIdOrderByOccurredOnAsc(a.getId()));
    }

    private PmsHodQueueItemResponse revisionItem(PmsGoalAssignmentEntity a, PmsTargetRevisionEntity revision,
                                                 List<PmsTargetRevisionRowEntity> revisionRows,
                                                 List<PmsGoalSheetHistoryEntity> history) {
        List<PmsTargetRevisionRowEntity> pending = revisionRows.stream()
                .filter(r -> r.getDecision() == null).toList();
        PmsTargetRevisionRowEntity first = pending.isEmpty() ? null : pending.get(0);
        return new PmsHodQueueItemResponse(a.getEmployeeUserId(), a.getEmployeeName(), a.getEmpCode(),
                a.getDesignationName(), a.getManagerName(), PmsSheetHistory.TARGET_REVISION,
                PmsTeamAccess.wire(a.getStatus()), !pending.isEmpty(),
                first == null ? null : new PmsHodQueueItemResponse.Change(first.getKpi().getName(),
                        first.getKpi().getUnit(), first.getPreviousTarget(), first.getRevisedTarget()),
                Math.max(0, pending.size() - 1), a.getManagerName(), PmsSheetHistory.MANAGER,
                revision.getRequestedOn().toLocalDate(), returnedCount(history));
    }

    private static int returnedCount(List<PmsGoalSheetHistoryEntity> history) {
        return (int) history.stream().filter(h -> "returned".equals(h.getAction())).count();
    }

    // ── review ───────────────────────────────────────────────────────────────

    /** The review screen of one request: changed and unchanged KRAs, who asked, and the reasons. */
    @Transactional(readOnly = true)
    public PmsHodReviewResponse review(PmsUserPrincipal hod, String employeeUserId, String financialYear,
                                       String requestType) {
        PmsGoalAssignmentEntity a = access.requireAssignment(hod.organisationId(), employeeUserId, financialYear);
        String template = access.templateOf(a).getTemplateName();
        List<PmsGoalChangeRequestEntity> requests =
                changeRequestRepository.findByAssignmentIdOrderByRequestedOnAscRevisionNoAsc(a.getId());
        List<PmsHodReviewResponse.Row> rows = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        String employeeRequest = null;
        String managerReply = null;
        LocalDate askedOn;
        int rounds;

        if (PmsSheetHistory.TARGET_REVISION.equals(requestType)) {
            PmsTargetRevisionEntity revision = revisionRepository.findFirstByAssignmentIdOrderByRequestedOnDesc(a.getId())
                    .orElseThrow(() -> DomainException.notFound("There is no target revision for this employee.",
                            "PMS_REVISION_NOT_FOUND"));
            Map<UUID, PmsTargetRevisionRowEntity> revised = new HashMap<>();
            revisionRowRepository.findByRevisionId(revision.getId()).forEach(r -> revised.put(r.getKpi().getId(), r));
            for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(a)) {
                PmsTargetRevisionRowEntity row = revised.get(t.getKpi().getId());
                totalWeight = totalWeight.add(t.getWeight());
                if (row == null) {
                    // Every KPI is listed; the ones the revision does not touch show their current target.
                    rows.add(changeRow(t, t.getTarget(), t.getTarget(), null, "none", null));
                    continue;
                }
                rows.add(changeRow(t, row.getPreviousTarget(), row.getRevisedTarget(), row.getReason(),
                        row.getDecision() == null ? "pending" : row.getDecision(), null));
            }
            askedOn = revision.getRequestedOn().toLocalDate();
            rounds = (int) historyRepository.countByAssignmentIdAndAction(a.getId(), "returned");
        } else {
            // A goal sheet is reviewed whole: every KPI is listed, with the history of the ones that moved.
            for (PmsGoalAssignmentTargetEntity t : access.targetsInOrder(a)) {
                totalWeight = totalWeight.add(t.getWeight());
                BigDecimal before = t.getOriginalTarget() != null ? t.getOriginalTarget() : t.getTarget();
                PmsGoalChangeRequestEntity latest = requests.stream()
                        .filter(r -> r.getKpi().getId().equals(t.getKpi().getId()))
                        .reduce((x, y) -> y).orElse(null);
                String reason = latest == null ? null : latest.getReason();
                BigDecimal asked = latest == null ? null : latest.getProposedTarget();
                rows.add(changeRow(t, before, t.getTarget(), isMoved(t) ? reason : null,
                        a.getStatus() == PmsAssignmentStatus.APPROVED ? "approved" : "pending", asked));
            }
            if (!requests.isEmpty()) {
                PmsGoalChangeRequestEntity last = requests.get(requests.size() - 1);
                employeeRequest = last.getReason();
                managerReply = last.getManagerRemarks();
            }
            askedOn = sheetItem(a).askedOn();
            rounds = requests.size() + (int) historyRepository.countByAssignmentIdAndAction(a.getId(), "returned");
        }
        // The last time the manager sent the sheet to the HOD, and who sent it.
        PmsGoalSheetHistoryEntity sent = historyRepository.findByAssignmentIdOrderByOccurredOnAsc(a.getId()).stream()
                .filter(h -> "sent_to_hod".equals(h.getAction()))
                .reduce((x, y) -> y).orElse(null);
        return new PmsHodReviewResponse(employeeUserId, a.getEmployeeName(), a.getEmpCode(), a.getDesignationName(),
                a.getManagerName(), template, financialYear, requestType, PmsTeamAccess.wire(a.getStatus()),
                a.getManagerName(), PmsSheetHistory.MANAGER, askedOn, employeeRequest, managerReply, rounds,
                totalWeight,
                a.getAcknowledgedOn() == null ? null : a.getAcknowledgedOn().toLocalDate(),
                sent == null ? null : sent.getOccurredOn().toLocalDate(),
                sent == null ? a.getManagerName() : sent.getActorName(), rows);
    }

    private static PmsHodReviewResponse.Row changeRow(PmsGoalAssignmentTargetEntity t, BigDecimal before,
                                                      BigDecimal after, String reason, String decision,
                                                      BigDecimal employeeRequested) {
        BigDecimal delta = before == null || after == null ? BigDecimal.ZERO : after.subtract(before);
        BigDecimal percent = before == null || before.signum() == 0 ? BigDecimal.ZERO
                : delta.multiply(BigDecimal.valueOf(100)).divide(before, 1, RoundingMode.HALF_UP);
        boolean changed = delta.signum() != 0;
        return new PmsHodReviewResponse.Row(t.getKpi().getId(), kraName(t), t.getKpi().getName(), t.getWeight(),
                t.getKpi().getUnit(), t.getKpi().getExpectedOutcome(), t.getKpi().getEvidenceRequired(),
                employeeRequested, before, after, delta, percent, reason, changed, changed ? decision : "none");
    }

    private static String kraName(PmsGoalAssignmentTargetEntity t) {
        return t.getKpi().getKra() == null ? null : t.getKpi().getKra().getName();
    }

    // ── decide ───────────────────────────────────────────────────────────────

    /** Approve a whole goal sheet, or the selected (default: all waiting) KPIs of a target revision. */
    @Transactional
    public PmsHodReviewResponse approve(PmsUserPrincipal hod, PmsHodActionRequest request) {
        decide(hod, request, true);
        return review(hod, request.employeeUserId(), request.financialYear(), request.requestType());
    }

    /** Return a goal sheet, or the selected KPIs of a target revision, to the manager. Remarks are required. */
    @Transactional
    public PmsHodReviewResponse returnToManager(PmsUserPrincipal hod, PmsHodActionRequest request) {
        decide(hod, request, false);
        return review(hod, request.employeeUserId(), request.financialYear(), request.requestType());
    }

    private void decide(PmsUserPrincipal hod, PmsHodActionRequest request, boolean approve) {
        String remarks = request.remarks() == null || request.remarks().isBlank() ? null : request.remarks().trim();
        if (!approve && remarks == null) {
            throw DomainException.unprocessable("Remarks are required when returning to the manager.",
                    "PMS_REMARKS_REQUIRED");
        }
        if (PmsSheetHistory.GOAL_SHEET.equals(request.requestType())) {
            PmsApprovalActionRequest sheet = new PmsApprovalActionRequest(request.employeeUserId(),
                    request.financialYear(), remarks);
            if (approve) {
                goalService.approve(hod, sheet);
            } else {
                goalService.returnToManager(hod, sheet);
            }
            return;
        }
        decideRevision(hod, request, approve, remarks);
    }

    private void decideRevision(PmsUserPrincipal hod, PmsHodActionRequest request, boolean approve, String remarks) {
        PmsGoalAssignmentEntity a = access.requireAssignment(hod.organisationId(), request.employeeUserId(),
                request.financialYear());
        PmsTargetRevisionEntity revision = pendingRevision(a).orElseThrow(() -> DomainException.conflict(
                "This target revision is not waiting for your decision.", "PMS_NOT_AWAITING_HOD"));
        List<PmsTargetRevisionRowEntity> rows = revisionRowRepository.findByRevisionId(revision.getId());
        Map<UUID, PmsTargetRevisionRowEntity> waiting = new LinkedHashMap<>();
        rows.stream().filter(r -> r.getDecision() == null).forEach(r -> waiting.put(r.getKpi().getId(), r));

        List<PmsTargetRevisionRowEntity> selected = new ArrayList<>();
        if (request.kpiIds() == null || request.kpiIds().isEmpty()) {
            selected.addAll(waiting.values());
        } else {
            for (UUID kpiId : new HashSet<>(request.kpiIds())) {
                PmsTargetRevisionRowEntity row = waiting.get(kpiId);
                if (row == null) {
                    throw DomainException.unprocessable("One of the selected KRAs is not waiting for a decision.",
                            "PMS_KPI_NOT_AWAITING_HOD");
                }
                selected.add(row);
            }
        }
        if (selected.isEmpty()) {
            throw DomainException.unprocessable("Select at least one KRA to act on.", "PMS_NOTHING_SELECTED");
        }
        for (PmsTargetRevisionRowEntity row : selected) {
            row.setDecision(approve ? "approved" : "returned");
            row.setHodRemarks(remarks);
            revisionRowRepository.save(row);
            sheetHistory.record(a, PmsSheetHistory.TARGET_REVISION, revision.getId(),
                    approve ? "approved" : "returned", hod.id(), hod.fullName(), PmsSheetHistory.HOD,
                    row.getKpi().getId(), row.getKpi().getName(), row.getKpi().getUnit(), row.getPreviousTarget(),
                    row.getRevisedTarget(), 0,
                    approve ? "Approved " + row.getKpi().getName() + " at " + PmsSheetHistory.num(row.getRevisedTarget())
                            : "Returned " + row.getKpi().getName() + " to the manager",
                    remarks);
        }
        boolean allDecided = rows.stream().noneMatch(r -> r.getDecision() == null);
        if (!allDecided) {
            return;
        }
        boolean anyApproved = rows.stream().anyMatch(r -> "approved".equals(r.getDecision()));
        boolean anyReturned = rows.stream().anyMatch(r -> "returned".equals(r.getDecision()));
        if (anyApproved) {
            revision.setStatus(PmsRevisionStatus.APPROVED);
            revision.setSource(PmsRevisionSource.HOD_APPROVED);
            revision.setApprovedBy(hod.fullName());
            revision.setApprovedOn(LocalDateTime.now());
        } else {
            revision.setStatus(PmsRevisionStatus.REJECTED);
        }
        revisionRepository.save(revision);
        if (anyReturned) {
            // The manager has to revise and send the returned KRAs again.
            Optional<PmsMidYearReviewEntity> review = midYearReviewRepository.findByAssignmentId(a.getId());
            review.ifPresent(r -> {
                r.setStatus(PmsMidYearStatus.NOT_STARTED);
                midYearReviewRepository.save(r);
            });
        }
        log.info("HOD {} finished the target revision of {} (approved: {}, returned: {})", hod.id(),
                request.employeeUserId(), anyApproved, anyReturned);
    }

    // ── bulk ─────────────────────────────────────────────────────────────────

    /** Approve Selected: each sheet is approved in its own transaction, and every outcome is reported. */
    public PmsBulkApproveResponse approveSelected(PmsUserPrincipal hod, PmsBulkApproveRequest request) {
        List<PmsBulkApproveResponse.Result> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PmsBulkApproveRequest.Item item : request.items()) {
            String name = assignmentRepository.findByOrganisationIdAndEmployeeUserIdAndFinancialYear(
                            hod.organisationId(), item.employeeUserId(), request.financialYear())
                    .map(PmsGoalAssignmentEntity::getEmployeeName).orElse(item.employeeUserId());
            if (!seen.add(item.employeeUserId() + "|" + item.requestType())) {
                results.add(new PmsBulkApproveResponse.Result(item.employeeUserId(), name, item.requestType(),
                        false, "Selected more than once"));
                continue;
            }
            try {
                transactions.executeWithoutResult(status -> decide(hod, new PmsHodActionRequest(item.employeeUserId(),
                        request.financialYear(), item.requestType(), null, null), true));
                results.add(new PmsBulkApproveResponse.Result(item.employeeUserId(), name, item.requestType(),
                        true, "Approved"));
            } catch (DomainException e) {
                results.add(new PmsBulkApproveResponse.Result(item.employeeUserId(), name, item.requestType(),
                        false, e.getMessage()));
            } catch (RuntimeException e) {
                log.warn("Bulk approve failed for {}: {}", item.employeeUserId(), e.getMessage());
                results.add(new PmsBulkApproveResponse.Result(item.employeeUserId(), name, item.requestType(),
                        false, "Could not approve this request"));
            }
        }
        int ok = (int) results.stream().filter(PmsBulkApproveResponse.Result::success).count();
        return new PmsBulkApproveResponse(ok, results.size() - ok, results);
    }

    // ── history and trail ────────────────────────────────────────────────────

    /** Decisions already made, newest first; one row per request (a decision on several KPIs is one row). */
    @Transactional(readOnly = true)
    public List<PmsHodHistoryItemResponse> history(PmsUserPrincipal hod, String financialYear, String requestType,
                                                   String decision, String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Map<String, PmsHodHistoryItemResponse> grouped = new LinkedHashMap<>();
        for (PmsGoalSheetHistoryEntity h : historyRepository
                .findByAssignmentOrganisationIdAndAssignmentFinancialYearAndActorRoleAndActionInOrderByOccurredOnDesc(
                        hod.organisationId(), financialYear, PmsSheetHistory.HOD, List.of("approved", "returned"))) {
            if (requestType != null && !requestType.isBlank() && !requestType.equals(h.getRequestType())) {
                continue;
            }
            if (decision != null && !decision.isBlank() && !decision.equals(h.getAction())) {
                continue;
            }
            PmsGoalAssignmentEntity a = h.getAssignment();
            if (!needle.isEmpty() && !(a.getEmployeeName() != null
                    && a.getEmployeeName().toLowerCase(Locale.ROOT).contains(needle))) {
                continue;
            }
            String key = a.getId() + "|" + h.getRequestType() + "|" + h.getRequestRef() + "|" + h.getAction() + "|"
                    + h.getOccurredOn().truncatedTo(ChronoUnit.MINUTES);
            PmsHodHistoryItemResponse existing = grouped.get(key);
            if (existing == null) {
                grouped.put(key, new PmsHodHistoryItemResponse(h.getOccurredOn().toLocalDate(),
                        a.getEmployeeUserId(), a.getEmployeeName(), a.getManagerName(), h.getRequestType(),
                        h.getKpiName(), h.getUnit(), h.getTargetBefore(), h.getTargetAfter(), h.getMoreChanges(),
                        h.getAction(), h.getRemarks()));
            } else {
                grouped.put(key, new PmsHodHistoryItemResponse(existing.decidedOn(), existing.employeeUserId(),
                        existing.employeeName(), existing.managerName(), existing.requestType(), existing.kpiName(),
                        existing.unit(), existing.from(), existing.to(), existing.moreChanges() + 1,
                        existing.decision(), existing.remarks()));
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /** The whole trail of one employee year: how each KPI moved, and each request with its steps. */
    @Transactional(readOnly = true)
    public PmsHodTrailResponse trail(PmsUserPrincipal hod, String employeeUserId, String financialYear) {
        PmsGoalAssignmentEntity a = access.requireAssignment(hod.organisationId(), employeeUserId, financialYear);
        List<PmsGoalSheetHistoryEntity> events = historyRepository.findByAssignmentIdOrderByOccurredOnAsc(a.getId());

        Map<String, List<PmsGoalSheetHistoryEntity>> groups = new LinkedHashMap<>();
        for (PmsGoalSheetHistoryEntity e : events) {
            groups.computeIfAbsent(e.getRequestType() + "|" + e.getRequestRef(), k -> new ArrayList<>()).add(e);
        }
        List<PmsHodTrailResponse.Request> requests = new ArrayList<>();
        int no = 0;
        for (List<PmsGoalSheetHistoryEntity> group : groups.values()) {
            no++;
            PmsGoalSheetHistoryEntity first = group.get(0);
            PmsGoalSheetHistoryEntity last = group.get(group.size() - 1);
            boolean sheet = PmsSheetHistory.GOAL_SHEET.equals(first.getRequestType());
            String status = "approved".equals(last.getAction()) ? "approved"
                    : "returned".equals(last.getAction()) ? "returned" : "waiting";
            List<PmsHodTrailResponse.Event> rows = new ArrayList<>();
            for (PmsGoalSheetHistoryEntity e : group) {
                rows.add(new PmsHodTrailResponse.Event(e.getOccurredOn().toLocalDate(), e.getActorName(),
                        e.getActorRole(), e.getAction(), e.getSummary()));
            }
            requests.add(new PmsHodTrailResponse.Request(no, first.getRequestType(),
                    sheet ? "Goal sheet" : "Target revision",
                    (sheet ? "Sent " : "Raised ") + first.getOccurredOn().toLocalDate() + " by " + first.getActorName(),
                    status, last.getOccurredOn().toLocalDate(), rows));
        }
        java.util.Collections.reverse(requests);
        return new PmsHodTrailResponse(employeeUserId, a.getEmployeeName(), a.getEmpCode(), a.getDesignationName(),
                a.getManagerName(), financialYear, moves(events), requests);
    }

    private static List<PmsHodTrailResponse.Move> moves(List<PmsGoalSheetHistoryEntity> events) {
        Map<UUID, List<PmsGoalSheetHistoryEntity>> byKpi = new LinkedHashMap<>();
        for (PmsGoalSheetHistoryEntity e : events) {
            if (e.getKpiId() != null) {
                byKpi.computeIfAbsent(e.getKpiId(), k -> new ArrayList<>()).add(e);
            }
        }
        List<PmsHodTrailResponse.Move> out = new ArrayList<>();
        for (List<PmsGoalSheetHistoryEntity> list : byKpi.values()) {
            boolean moved = list.stream().anyMatch(e -> e.getTargetBefore() != null && e.getTargetAfter() != null
                    && e.getTargetBefore().compareTo(e.getTargetAfter()) != 0);
            if (!moved) {
                continue;
            }
            List<PmsHodTrailResponse.Step> steps = new ArrayList<>();
            PmsGoalSheetHistoryEntity first = list.get(0);
            steps.add(new PmsHodTrailResponse.Step(first.getOccurredOn().toLocalDate(), first.getTargetBefore(),
                    PmsSheetHistory.MANAGER, "First target sent"));
            for (PmsGoalSheetHistoryEntity e : list) {
                steps.add(new PmsHodTrailResponse.Step(e.getOccurredOn().toLocalDate(), e.getTargetAfter(),
                        e.getActorRole(), stepLabel(e)));
            }
            out.add(new PmsHodTrailResponse.Move(first.getKpiName(), first.getUnit(), steps));
        }
        return out;
    }

    private static String stepLabel(PmsGoalSheetHistoryEntity e) {
        boolean lower = e.getTargetBefore() != null && e.getTargetAfter() != null
                && e.getTargetAfter().compareTo(e.getTargetBefore()) < 0;
        return switch (e.getAction()) {
            case "requested_change" -> "Employee asked for " + (lower ? "less" : "more");
            case "answered" -> "Manager settled";
            case "revision_requested" -> "Manager asked to revise";
            case "approved" -> "HOD approved";
            case "returned" -> "HOD returned";
            default -> "Updated";
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Optional<PmsTargetRevisionEntity> pendingRevision(PmsGoalAssignmentEntity a) {
        return revisionRepository.findFirstByAssignmentIdOrderByRequestedOnDesc(a.getId())
                .filter(r -> r.getStatus() == PmsRevisionStatus.PENDING_HOD);
    }

    /** A target that is no longer the one the manager first assigned. */
    private static boolean isMoved(PmsGoalAssignmentTargetEntity t) {
        return t.getOriginalTarget() != null && t.getTarget() != null
                && t.getTarget().compareTo(t.getOriginalTarget()) != 0;
    }
}
