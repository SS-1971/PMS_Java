package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsCycleEntity;
import com.sentrifugo.db.entity.PmsCycleStageEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentEntity;
import com.sentrifugo.db.entity.PmsGoalAssignmentTargetEntity;
import com.sentrifugo.db.entity.PmsGoalProgressEntity;
import com.sentrifugo.db.entity.PmsGoalTransferEntity;
import com.sentrifugo.db.entity.PmsGoalTemplateEntity;
import com.sentrifugo.db.entity.PmsGoalTemplateKpiEntity;
import com.sentrifugo.db.entity.PmsKpiMasterEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionEntity;
import com.sentrifugo.db.entity.PmsTargetRevisionRowEntity;
import com.sentrifugo.db.enums.PmsAssignmentStatus;
import com.sentrifugo.db.enums.PmsCycleStageType;
import com.sentrifugo.db.enums.PmsCycleStatus;
import com.sentrifugo.db.enums.PmsRevisionSource;
import com.sentrifugo.db.enums.PmsRevisionStatus;
import com.sentrifugo.db.enums.PmsProgressPeriod;
import com.sentrifugo.db.enums.PmsTransferStatus;
import com.sentrifugo.db.enums.PmsTemplateStatus;
import com.sentrifugo.db.repository.PmsCycleRepository;
import com.sentrifugo.db.repository.PmsCycleStageRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRepository;
import com.sentrifugo.db.repository.PmsTargetRevisionRowRepository;
import com.sentrifugo.db.repository.PmsGoalAssignmentTargetRepository;
import com.sentrifugo.db.repository.PmsGoalChangeRequestRepository;
import com.sentrifugo.db.entity.PmsGoalChangeRequestEntity;
import com.sentrifugo.db.enums.PmsChangeDecision;
import com.sentrifugo.db.enums.PmsChangeRequestStatus;
import com.sentrifugo.pms.model.PmsChangeRequestResponse;
import com.sentrifugo.db.repository.PmsGoalProgressRepository;
import com.sentrifugo.db.repository.PmsGoalTransferRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateKpiRepository;
import com.sentrifugo.db.repository.PmsGoalTemplateRepository;
import com.sentrifugo.integration.model.IamEmployee;
import com.sentrifugo.integration.IamEmployeeClient;
import com.sentrifugo.pms.model.PmsApprovalActionRequest;
import com.sentrifugo.pms.model.PmsApprovalItemResponse;
import com.sentrifugo.pms.model.PmsChangeRequest;
import com.sentrifugo.pms.model.PmsEmployeeTargetsResponse;
import com.sentrifugo.pms.model.PmsGoalDecisionRequest;
import com.sentrifugo.pms.model.PmsSendToHodRequest;
import com.sentrifugo.pms.model.PmsTargetValidationResponse;
import com.sentrifugo.pms.model.PmsTemplateAssignRequest;
import com.sentrifugo.pms.model.PmsTemplateAssignmentResponse;
import com.sentrifugo.pms.model.PmsTargetsRequest;
import com.sentrifugo.pms.model.PmsTeamMemberResponse;
import com.sentrifugo.security.context.PmsUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Goal assignment by the manager (screens 3.1 - 3.5) and the approval steps after it (4.x, 5.x). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PmsGoalAssignmentService {

    /** Goal state before any targets are saved for the employee. */
    static final String NOT_STARTED = "not_started";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final int HISTORY_YEARS = 3;
    /** Statuses in which the sheet is not the manager's to edit: sent, acknowledged, with the HOD, approved. */
    private static final List<PmsAssignmentStatus> EMPLOYEE_SIDE = List.of(PmsAssignmentStatus.SENT_TO_EMPLOYEE,
            PmsAssignmentStatus.ACKNOWLEDGED, PmsAssignmentStatus.WITH_HOD, PmsAssignmentStatus.APPROVED);
    /** The HOD decides only on sheets the manager has sent: an acknowledged sheet waits for the manager first. */
    private static final List<PmsAssignmentStatus> HOD_QUEUE = List.of(PmsAssignmentStatus.WITH_HOD);

    private final IamEmployeeClient iam;
    private final PmsGoalAssignmentRepository assignmentRepository;
    private final PmsGoalAssignmentTargetRepository targetRepository;
    private final PmsGoalChangeRequestRepository changeRequestRepository;
    private final PmsGoalTemplateRepository templateRepository;
    private final PmsGoalTemplateKpiRepository templateKpiRepository;
    private final PmsGoalTransferRepository transferRepository;
    private final PmsGoalProgressRepository progressRepository;
    private final PmsCycleRepository cycleRepository;
    private final PmsCycleStageRepository cycleStageRepository;
    private final PmsTargetRevisionRepository revisionRepository;
    private final PmsTargetRevisionRowRepository revisionRowRepository;
    private final PmsSheetHistory sheetHistory;

    /**
     * Screen 3.1 "My Team" (and 3.6): the caller's direct reports with their goal status. A sheet the
     * HOD returned shows as {@code returned_by_hod}; an employee transferred in whose period 2 KRAs are
     * not sent yet shows as {@code transferred_in}.
     */
    @Transactional(readOnly = true)
    public List<PmsTeamMemberResponse> getTeam(PmsUserPrincipal manager, String financialYear) {
        List<IamEmployee> reports = iam.directReports(manager.organisationId(), manager.id());
        Map<String, PmsGoalAssignmentEntity> assignmentByEmployee = new HashMap<>();
        Map<String, PmsGoalTransferEntity> transferByEmployee = new HashMap<>();
        Set<UUID> withTargets = new HashSet<>();
        if (!reports.isEmpty()) {
            List<String> ids = reports.stream().map(IamEmployee::userId).toList();
            for (PmsGoalAssignmentEntity a : assignmentRepository
                    .findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(
                            manager.organisationId(), financialYear, ids)) {
                assignmentByEmployee.put(a.getEmployeeUserId(), a);
            }
            for (PmsGoalTransferEntity t : transferRepository
                    .findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(
                            manager.organisationId(), financialYear, ids)) {
                transferByEmployee.put(t.getEmployeeUserId(), t);
            }
            if (!assignmentByEmployee.isEmpty()) {
                withTargets.addAll(targetRepository.findAssignmentIdsWithTargets(
                        assignmentByEmployee.values().stream().map(PmsGoalAssignmentEntity::getId).toList()));
            }
        }
        Map<String, PmsGoalTemplateEntity> templateByRole = new HashMap<>();
        Map<UUID, PmsGoalTemplateEntity> assignedTemplates = new HashMap<>();
        return reports.stream()
                .map(e -> {
                    PmsGoalAssignmentEntity a = assignmentByEmployee.get(e.userId());
                    // The assigned template when there is one, else the role's active template.
                    PmsGoalTemplateEntity template = null;
                    if (a != null) {
                        template = assignedTemplates.computeIfAbsent(a.getTemplateId(),
                                id -> templateRepository.findById(id).orElse(null));
                    }
                    if (template == null) {
                        template = activeTemplateOfRole(manager.organisationId(), e.designationId(), financialYear,
                                templateByRole);
                    }
                    return toTeamMember(e,
                            teamStatus(a, transferByEmployee.get(e.userId()),
                                    a != null && withTargets.contains(a.getId())),
                            template, a != null);
                })
                .toList();
    }

    /**
     * "Create &amp; assign template": gives the template named in the request to every direct report in that
     * role who does not have one for the year yet. Employees whose goal sheet already exists keep theirs.
     * The template must be an active template of the caller's organisation for that role and year.
     */
    @Transactional
    public PmsTemplateAssignmentResponse assignTemplate(PmsUserPrincipal manager, PmsTemplateAssignRequest request) {
        List<IamEmployee> inRole = iam.directReports(manager.organisationId(), manager.id()).stream()
                .filter(e -> request.designationId().equals(e.designationId()))
                .toList();
        if (inRole.isEmpty()) {
            throw DomainException.unprocessable("None of your team members has this role.", "PMS_ROLE_NOT_IN_TEAM");
        }
        PmsGoalTemplateEntity template = templateRepository.findById(request.templateId())
                .filter(t -> manager.organisationId().equals(t.getOrganisationId()))
                .orElseThrow(() -> DomainException.notFound("Goal template not found", "PMS_TEMPLATE_NOT_FOUND"));
        if (template.getStatus() != PmsTemplateStatus.ACTIVE
                || !request.designationId().equals(template.getRoleId())
                || !request.financialYear().equals(template.getFinancialYear())) {
            throw DomainException.unprocessable("That template is not the active template of this role for "
                    + request.financialYear() + ".", "PMS_TEMPLATE_ROLE_MISMATCH");
        }

        Set<String> alreadyHave = new HashSet<>();
        assignmentRepository.findByOrganisationIdAndFinancialYearAndEmployeeUserIdIn(manager.organisationId(),
                        request.financialYear(), inRole.stream().map(IamEmployee::userId).toList())
                .forEach(a -> alreadyHave.add(a.getEmployeeUserId()));

        int assigned = 0;
        for (IamEmployee e : inRole) {
            if (alreadyHave.contains(e.userId())) {
                continue;
            }
            assignmentRepository.save(PmsGoalAssignmentEntity.builder()
                    .organisationId(manager.organisationId())
                    .employeeUserId(e.userId())
                    .managerUserId(manager.id())
                    .financialYear(request.financialYear())
                    .templateId(template.getId())
                    .status(PmsAssignmentStatus.DRAFT)
                    .employeeName(fullName(e))
                    .empCode(e.empCode())
                    .designationName(e.designationName())
                    .departmentId(e.departmentId())
                    .managerName(manager.fullName())
                    .build());
            assigned++;
        }
        log.info("Manager {} assigned template {} to {} employees ({} already had one)", manager.id(),
                template.getId(), assigned, alreadyHave.size());
        return new PmsTemplateAssignmentResponse(request.designationId(), template.getId(),
                template.getTemplateName(), assigned, alreadyHave.size());
    }

    /** Screen 3.6 "Send Selected to HOD": acknowledged sheets move to the HOD queue. */
    @Transactional
    public List<String> sendToHod(PmsUserPrincipal manager, PmsSendToHodRequest request) {
        List<String> sent = new ArrayList<>();
        String note = blankToNull(request.remarks());
        for (String employeeUserId : request.employeeUserIds().stream().distinct().toList()) {
            requireTeamMember(manager, employeeUserId);
            PmsGoalAssignmentEntity a = requireAssignment(manager.organisationId(), employeeUserId,
                    request.financialYear());
            if (a.getStatus() != PmsAssignmentStatus.ACKNOWLEDGED) {
                throw DomainException.unprocessable(
                        "Only acknowledged goal sheets can be sent to the HOD (" + a.getEmployeeName() + ").",
                        "PMS_INVALID_GOAL_STATE");
            }
            requireCompleteSheet(a);
            if (!hasRevisedKras(a)) {
                // Nothing was revised, so there is nothing to put in front of the HOD.
                approveWithoutHod(a, manager.id(), manager.fullName(), PmsSheetHistory.MANAGER);
                sent.add(employeeUserId);
                continue;
            }
            a.setStatus(PmsAssignmentStatus.WITH_HOD);
            assignmentRepository.save(a);
            sheetHistory.sheet(a, "sent_to_hod", manager.id(), manager.fullName(), PmsSheetHistory.MANAGER,
                    note == null ? "Sent the goal sheet to the HOD" : "Sent the goal sheet to the HOD with a note",
                    note);
            sent.add(employeeUserId);
        }
        log.info("Manager {} sent {} goal sheets to the HOD", manager.id(), sent.size());
        return sent;
    }

    /** True when any KRA's target moved after the manager first assigned it: what the HOD is asked to approve. */
    private boolean hasRevisedKras(PmsGoalAssignmentEntity a) {
        return targetRepository.findByAssignmentId(a.getId()).stream()
                .anyMatch(t -> t.getOriginalTarget() != null && t.getTarget() != null
                        && t.getTarget().compareTo(t.getOriginalTarget()) != 0);
    }

    /** Approves a sheet with no revised KRAs: goals are frozen and progress tracking can open. */
    private void approveWithoutHod(PmsGoalAssignmentEntity a, String actorUserId, String actorName,
                                   String actorRole) {
        a.setStatus(PmsAssignmentStatus.APPROVED);
        a.setApprovedOn(LocalDateTime.now());
        a.setHodRemarks(null);
        assignmentRepository.save(a);
        sheetHistory.sheet(a, "approved", actorUserId, actorName, actorRole,
                "Goals approved: no KRA was revised, so nothing needed the HOD", null);
    }

    /** A sheet goes to the HOD only complete: every target set and the weights adding up to 100. */
    private void requireCompleteSheet(PmsGoalAssignmentEntity a) {
        List<PmsGoalAssignmentTargetEntity> rows = targetRepository.findByAssignmentId(a.getId());
        BigDecimal total = rows.stream().map(PmsGoalAssignmentTargetEntity::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean missingTarget = rows.stream().anyMatch(t -> t.getTarget() == null);
        if (rows.isEmpty() || missingTarget || total.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw DomainException.unprocessable("The goal sheet of " + a.getEmployeeName()
                    + " is incomplete: every target must be set and the weightage must total 100%.",
                    "PMS_TARGETS_INVALID");
        }
    }

    /** Screens 3.2 and 3.3: the employee's template KPIs with any targets already saved. */
    @Transactional(readOnly = true)
    public PmsEmployeeTargetsResponse getEmployeeTargets(PmsUserPrincipal manager, String employeeUserId,
                                                         String financialYear) {
        IamEmployee employee = requireTeamMember(manager, employeeUserId);
        Optional<PmsGoalAssignmentEntity> assignment = assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(manager.organisationId(), employeeUserId,
                        financialYear);
        PmsGoalTemplateEntity template = assignment.isPresent()
                ? requireTemplateById(assignment.get())
                : requireTemplate(manager, employee, financialYear);
        return view(assignment, template, manager.organisationId(), employeeUserId, financialYear,
                fullName(employee), employee.empCode(), employee.designationName(), manager.fullName());
    }

    /** Screen 4.1 "My Goals": the signed-in employee's own targets for the year. */
    @Transactional(readOnly = true)
    public PmsEmployeeTargetsResponse getMyGoals(PmsUserPrincipal employee, String financialYear) {
        Optional<PmsGoalAssignmentEntity> assignment = assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(employee.organisationId(), employee.id(),
                        financialYear);
        if (assignment.isEmpty()) {
            throw DomainException.notFound("No goals have been set for you for " + financialYear,
                    "PMS_GOALS_NOT_FOUND");
        }
        PmsGoalTemplateEntity template = templateRepository.findById(assignment.get().getTemplateId())
                .orElseThrow(() -> DomainException.notFound("Goal template not found", "PMS_TEMPLATE_NOT_FOUND"));
        return view(assignment, template, employee.organisationId(), employee.id(), financialYear,
                null, null, null, null);
    }

    /** Screen 4.3 "Acknowledge and submit": the employee accepts the goals sent to them. */
    @Transactional
    public PmsEmployeeTargetsResponse acknowledge(PmsUserPrincipal employee, PmsGoalDecisionRequest request) {
        PmsGoalAssignmentEntity a = requireAssignment(employee.organisationId(), employee.id(),
                request.financialYear());
        if (a.getStatus() == PmsAssignmentStatus.APPROVED) {
            return acknowledgeRevision(employee, a, request.financialYear());
        }
        requireStatus(a, PmsAssignmentStatus.SENT_TO_EMPLOYEE, "Only goals sent to you can be acknowledged.");
        a.setStatus(PmsAssignmentStatus.ACKNOWLEDGED);
        a.setAcknowledgedOn(LocalDateTime.now());
        a.setAcknowledgeComment(blankToNull(request.comment()));
        assignmentRepository.save(a);
        sheetHistory.sheet(a, "acknowledged", employee.id(), a.getEmployeeName(), PmsSheetHistory.EMPLOYEE,
                !changeRequestRepository.findByAssignmentIdOrderByRequestedOnAscRevisionNoAsc(a.getId()).isEmpty()
                        ? "Acknowledged the revised goals" : "Acknowledged the goals",
                blankToNull(request.comment()));
        for (PmsGoalChangeRequestEntity answered : changeRequestRepository
                .findByAssignmentIdAndStatus(a.getId(), PmsChangeRequestStatus.RESPONDED)) {
            if (answered.getAcknowledgedOn() == null) {
                answered.setAcknowledgedOn(LocalDateTime.now());
                changeRequestRepository.save(answered);
            }
        }
        // Acknowledging the sheet also acknowledges any revision the manager made to it.
        for (PmsTargetRevisionEntity pending : revisionRepository
                .findByAssignmentIdAndStatusAndAcknowledgedOnIsNull(a.getId(), PmsRevisionStatus.APPROVED)) {
            pending.setAcknowledgedOn(LocalDateTime.now());
            revisionRepository.save(pending);
        }
        // Only revised KRAs go to the HOD. A sheet nobody revised has nothing for the HOD to decide.
        if (!hasRevisedKras(a)) {
            approveWithoutHod(a, employee.id(), a.getEmployeeName(), PmsSheetHistory.EMPLOYEE);
        }
        log.info("Employee {} acknowledged goals for {} (manager {})", employee.id(), request.financialYear(),
                a.getManagerUserId());
        return getMyGoals(employee, request.financialYear());
    }

    /** Screen 4.2 "Request change": the employee asks the manager to revise one target. */
    @Transactional
    public PmsEmployeeTargetsResponse requestChange(PmsUserPrincipal employee, PmsChangeRequest request) {
        PmsGoalAssignmentEntity a = requireAssignment(employee.organisationId(), employee.id(),
                request.financialYear());
        requireStatus(a, PmsAssignmentStatus.SENT_TO_EMPLOYEE, "Only goals sent to you can be questioned.");
        if (!allowedKpis(requireTemplateById(a)).containsKey(request.kpiId())) {
            throw DomainException.unprocessable("That KPI is not on your goal sheet.", "PMS_KPI_NOT_IN_TEMPLATE");
        }
        if (changeRequestRepository.existsByAssignmentIdAndKpiIdAndStatus(a.getId(), request.kpiId(),
                PmsChangeRequestStatus.REQUESTED)) {
            throw DomainException.conflict("A change request for this KPI is already waiting for your manager.",
                    "PMS_REVISION_PENDING");
        }
        snapshotSentTargets(a);
        BigDecimal seen = targetRepository.findByAssignmentId(a.getId()).stream()
                .filter(t -> t.getKpi().getId().equals(request.kpiId()))
                .map(PmsGoalAssignmentTargetEntity::getTarget)
                .findFirst().orElse(null);
        changeRequestRepository.save(PmsGoalChangeRequestEntity.builder()
                .assignment(a)
                .kpi(allowedKpis(requireTemplateById(a)).get(request.kpiId()))
                .revisionNo((int) changeRequestRepository.countByAssignmentIdAndKpiId(a.getId(), request.kpiId()) + 1)
                .requestedBy(employee.id())
                .requestedOn(LocalDateTime.now())
                .targetAtRequest(seen)
                .proposedTarget(request.proposedTarget())
                .reason(request.reason().trim())
                .status(PmsChangeRequestStatus.REQUESTED)
                .build());
        PmsKpiMasterEntity askedKpi = allowedKpis(requireTemplateById(a)).get(request.kpiId());
        sheetHistory.record(a, PmsSheetHistory.GOAL_SHEET, null, "requested_change", employee.id(),
                a.getEmployeeName(), PmsSheetHistory.EMPLOYEE, askedKpi.getId(), askedKpi.getName(),
                askedKpi.getUnit(), seen, request.proposedTarget(), 0,
                "Asked to change " + askedKpi.getName() + " " + PmsSheetHistory.num(seen) + " -> "
                        + PmsSheetHistory.num(request.proposedTarget()) + ". Reason: " + request.reason().trim(),
                request.reason().trim());
        a.setStatus(PmsAssignmentStatus.CHANGE_REQUESTED);
        a.setChangeKpiId(request.kpiId());
        a.setChangeProposedTarget(request.proposedTarget());
        a.setChangeReason(request.reason().trim());
        a.setChangeRequestedOn(LocalDateTime.now());
        assignmentRepository.save(a);
        snapshotSentTargets(a);
        log.info("Employee {} requested a change to KPI {} for {} (manager {})", employee.id(), request.kpiId(),
                request.financialYear(), a.getManagerUserId());
        return getMyGoals(employee, request.financialYear());
    }

    /** The signed-in employee's own change requests for the year, oldest first. */
    @Transactional(readOnly = true)
    public List<PmsChangeRequestResponse> myChangeRequests(PmsUserPrincipal employee, String financialYear) {
        return assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(employee.organisationId(), employee.id(),
                        financialYear)
                .map(this::changeRequestsOf).orElse(List.of());
    }

    /** One direct report's change requests for the year, for the manager's revise screen. */
    @Transactional(readOnly = true)
    public List<PmsChangeRequestResponse> changeRequestsOfReport(PmsUserPrincipal manager, String employeeUserId,
                                                                 String financialYear) {
        requireTeamMember(manager, employeeUserId);
        return assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(manager.organisationId(), employeeUserId,
                        financialYear)
                .map(this::changeRequestsOf).orElse(List.of());
    }

    private List<PmsChangeRequestResponse> changeRequestsOf(PmsGoalAssignmentEntity a) {
        Map<UUID, PmsKpiMasterEntity> kpis = allowedKpis(requireTemplateById(a));
        return changeRequestRepository.findByAssignmentIdOrderByRequestedOnAscRevisionNoAsc(a.getId()).stream()
                .map(r -> new PmsChangeRequestResponse(r.getKpi().getId(),
                        kpis.containsKey(r.getKpi().getId()) && r.getKpi().getKra() != null
                                ? r.getKpi().getKra().getName() : null,
                        r.getKpi().getName(), r.getRevisionNo(), r.getRequestedOn(), r.getReason(),
                        r.getProposedTarget(), r.getTargetAtRequest(), wire(r.getStatus()),
                        r.getDecision() == null ? null : wire(r.getDecision()), r.getFinalTarget(),
                        r.getManagerRemarks(), r.getRespondedOn(), r.getAcknowledgedOn()))
                .toList();
    }

    /** Screen 5.1 "Team Goals Settings" (HOD): every sheet waiting for HOD approval. */
    @Transactional(readOnly = true)
    public List<PmsApprovalItemResponse> approvalQueue(PmsUserPrincipal hod, String financialYear) {
        return assignmentRepository.findByOrganisationIdAndFinancialYearAndStatusIn(
                        hod.organisationId(), financialYear, HOD_QUEUE)
                .stream()
                .map(a -> new PmsApprovalItemResponse(a.getEmployeeUserId(), a.getEmployeeName(), a.getEmpCode(),
                        a.getDesignationName(), a.getManagerUserId(), wire(a.getStatus())))
                .toList();
    }

    /** Screen 5.2 approve: the HOD approves the employee's goals. */
    @Transactional
    public PmsEmployeeTargetsResponse approve(PmsUserPrincipal hod, PmsApprovalActionRequest request) {
        PmsGoalAssignmentEntity a = requireAssignment(hod.organisationId(), request.employeeUserId(),
                request.financialYear());
        if (!HOD_QUEUE.contains(a.getStatus())) {
            throw DomainException.unprocessable("These goals are not waiting for HOD approval.",
                    "PMS_NOT_AWAITING_HOD");
        }
        a.setStatus(PmsAssignmentStatus.APPROVED);
        a.setApprovedOn(LocalDateTime.now());
        a.setHodRemarks(blankToNull(request.remarks()));
        assignmentRepository.save(a);
        recordHodDecision(hod, a, "approved", "Approved. Goals frozen", blankToNull(request.remarks()));
        return view(Optional.of(a), requireTemplateById(a), hod.organisationId(), a.getEmployeeUserId(),
                request.financialYear(), a.getEmployeeName(), a.getEmpCode(), a.getDesignationName(), null);
    }

    /** Screen 5.2 return: the HOD sends the goals back to the manager to revise. */
    @Transactional
    public PmsEmployeeTargetsResponse returnToManager(PmsUserPrincipal hod, PmsApprovalActionRequest request) {
        PmsGoalAssignmentEntity a = requireAssignment(hod.organisationId(), request.employeeUserId(),
                request.financialYear());
        if (!HOD_QUEUE.contains(a.getStatus())) {
            throw DomainException.unprocessable("These goals are not waiting for HOD approval.",
                    "PMS_NOT_AWAITING_HOD");
        }
        if (blankToNull(request.remarks()) == null) {
            throw DomainException.unprocessable("Remarks are required when returning goals to the manager.",
                    "PMS_REMARKS_REQUIRED");
        }
        a.setStatus(PmsAssignmentStatus.DRAFT);
        a.setHodRemarks(blankToNull(request.remarks()));
        assignmentRepository.save(a);
        recordHodDecision(hod, a, "returned", "Returned to the manager for target revision",
                blankToNull(request.remarks()));
        snapshotSentTargets(a);
        return view(Optional.of(a), requireTemplateById(a), hod.organisationId(), a.getEmployeeUserId(),
                request.financialYear(), a.getEmployeeName(), a.getEmpCode(), a.getDesignationName(), null);
    }

    /** Screen 3.3 "Save Draft": stores the targets as given. Incomplete drafts are allowed. */
    @Transactional
    public PmsEmployeeTargetsResponse saveDraft(PmsUserPrincipal manager, PmsTargetsRequest request) {
        IamEmployee employee = requireTeamMember(manager, request.employeeUserId());
        PmsGoalAssignmentEntity assignment = assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(manager.organisationId(),
                        request.employeeUserId(), request.financialYear())
                .orElseThrow(() -> DomainException.conflict(
                        "Create and assign the role template to this employee before setting targets.",
                        "PMS_TEMPLATE_NOT_ASSIGNED"));
        if (EMPLOYEE_SIDE.contains(assignment.getStatus())) {
            throw DomainException.conflict("These goals are with the employee or the HOD and cannot be edited now.",
                    "PMS_INVALID_GOAL_STATE");
        }
        PmsGoalTemplateEntity template = requireTemplateById(assignment);
        Map<UUID, PmsKpiMasterEntity> allowed = allowedKpis(template);
        checkKpisBelong(request, allowed);

        assignment.setEmployeeName(fullName(employee));
        assignment.setEmpCode(employee.empCode());
        assignment.setDesignationName(employee.designationName());
        assignment.setDepartmentId(employee.departmentId());
        assignment.setManagerName(manager.fullName());
        assignment.setStatus(PmsAssignmentStatus.DRAFT);
        assignment = assignmentRepository.save(assignment);

        // Rewriting the rows must not lose what the employee last saw: it is the baseline for a revision.
        Map<UUID, BigDecimal> lastSent = new HashMap<>();
        Map<UUID, BigDecimal> originals = new HashMap<>();
        for (PmsGoalAssignmentTargetEntity old : targetRepository.findByAssignmentId(assignment.getId())) {
            if (old.getSentTarget() != null) {
                lastSent.put(old.getKpi().getId(), old.getSentTarget());
            }
            if (old.getOriginalTarget() != null) {
                originals.put(old.getKpi().getId(), old.getOriginalTarget());
            }
        }
        targetRepository.deleteByAssignmentId(assignment.getId());
        targetRepository.flush();
        for (PmsTargetsRequest.Target t : request.targets()) {
            targetRepository.save(PmsGoalAssignmentTargetEntity.builder()
                    .assignment(assignment)
                    .kpi(allowed.get(t.kpiId()))
                    .weight(t.weight())
                    .target(t.target())
                    .sentTarget(lastSent.get(t.kpiId()))
                    .originalTarget(originals.get(t.kpiId()))
                    .build());
        }
        log.info("Saved draft targets for employee {} ({} KPIs)", request.employeeUserId(), request.targets().size());
        return getEmployeeTargets(manager, request.employeeUserId(), request.financialYear());
    }

    /** Screen 3.4 "Validate": the checks a send needs to pass. Writes nothing. */
    @Transactional(readOnly = true)
    public PmsTargetValidationResponse validate(PmsUserPrincipal manager, PmsTargetsRequest request) {
        IamEmployee employee = requireTeamMember(manager, request.employeeUserId());
        Optional<PmsGoalAssignmentEntity> existing = assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(manager.organisationId(),
                        request.employeeUserId(), request.financialYear());
        PmsGoalTemplateEntity template = existing.isPresent()
                ? requireTemplateById(existing.get())
                : requireTemplate(manager, employee, request.financialYear());
        Map<UUID, PmsKpiMasterEntity> allowed = allowedKpis(template);
        checkKpisBelong(request, allowed);

        List<String> errors = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PmsTargetsRequest.Target t : request.targets()) {
            String kpiName = allowed.get(t.kpiId()).getName();
            total = total.add(t.weight());
            if (t.weight().signum() <= 0) {
                errors.add("Weight for " + kpiName + " must be above 0");
            }
            if (t.target() == null) {
                errors.add("Target missing for " + kpiName);
            }
        }
        if (total.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            errors.add("Total weightage is " + total.stripTrailingZeros().toPlainString() + "% (must be 100%)");
        }
        return new PmsTargetValidationResponse(errors.isEmpty(), total, errors);
    }

    /**
     * Screen 3.5 "Save and Send": refuses to send unless validation passes, then saves the
     * targets and marks them sent for the employee to acknowledge.
     */
    @Transactional
    public PmsEmployeeTargetsResponse send(PmsUserPrincipal manager, PmsTargetsRequest request) {
        PmsTargetValidationResponse check = validate(manager, request);
        if (!check.valid()) {
            throw new DomainException(String.join("; ", check.errors()), "PMS_TARGETS_INVALID",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        saveDraft(manager, request);
        PmsGoalAssignmentEntity assignment = assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(manager.organisationId(),
                        request.employeeUserId(), request.financialYear())
                .orElseThrow();
        // A sheet that was sent before and is being sent again is a revision: show the employee what moved.
        List<PmsGoalAssignmentTargetEntity> rows = targetRepository.findByAssignmentId(assignment.getId());
        Map<UUID, BigDecimal> lastSent = new HashMap<>();
        rows.stream().filter(t -> t.getSentTarget() != null)
                .forEach(t -> lastSent.put(t.getKpi().getId(), t.getSentTarget()));
        if (!lastSent.isEmpty()) {
            String fallbackReason = assignment.getChangeReason() != null ? "Revised after your change request"
                    : assignment.getHodRemarks() != null ? "Revised after HOD review" : "Targets revised by your manager";
            recordManagerRevision(manager, assignment, request, lastSent, fallbackReason);
        }
        Map<UUID, BigDecimal> finalTargets = new HashMap<>();
        for (PmsGoalAssignmentTargetEntity t : rows) {
            if (t.getOriginalTarget() == null) {
                // First time this KPI is sent (or an older sheet): what the employee saw is the original.
                t.setOriginalTarget(t.getSentTarget() != null ? t.getSentTarget() : t.getTarget());
            }
            finalTargets.put(t.getKpi().getId(), t.getTarget());
            t.setSentTarget(t.getTarget());
            targetRepository.save(t);
        }
        Map<UUID, String> remarkByKpi = new HashMap<>();
        request.targets().forEach(t -> remarkByKpi.put(t.kpiId(), blankToNull(t.remarks())));
        for (PmsGoalChangeRequestEntity open : changeRequestRepository.findByAssignmentIdAndStatus(
                assignment.getId(), PmsChangeRequestStatus.REQUESTED)) {
            BigDecimal answer = finalTargets.get(open.getKpi().getId());
            boolean moved = answer != null && open.getTargetAtRequest() != null
                    && answer.compareTo(open.getTargetAtRequest()) != 0;
            open.setStatus(PmsChangeRequestStatus.RESPONDED);
            open.setDecision(moved ? PmsChangeDecision.REVISED : PmsChangeDecision.UNCHANGED);
            open.setFinalTarget(answer);
            open.setManagerRemarks(remarkByKpi.get(open.getKpi().getId()));
            open.setRespondedBy(manager.id());
            open.setRespondedOn(LocalDateTime.now());
            changeRequestRepository.save(open);
            sheetHistory.record(assignment, PmsSheetHistory.GOAL_SHEET, null, "answered", manager.id(),
                    manager.fullName(), PmsSheetHistory.MANAGER, open.getKpi().getId(), open.getKpi().getName(),
                    open.getKpi().getUnit(), open.getTargetAtRequest(), answer, 0,
                    (moved ? "Settled on " : "Kept ") + PmsSheetHistory.num(answer) + " for "
                            + open.getKpi().getName() + " and resent to " + assignment.getEmployeeName(),
                    open.getManagerRemarks());
        }
        sheetHistory.sheet(assignment, "sent", manager.id(), manager.fullName(), PmsSheetHistory.MANAGER,
                (lastSent.isEmpty() ? "Sent the goals to " : "Resent the revised goals to ")
                        + assignment.getEmployeeName(), null);
        assignment.setStatus(PmsAssignmentStatus.SENT_TO_EMPLOYEE);
        // The HOD's remarks were answered by this resend; they stay on the trail but no longer mark the sheet.
        assignment.setHodRemarks(null);
        assignment.setSentOn(LocalDateTime.now());
        assignment.setChangeKpiId(null);
        assignment.setChangeReason(null);
        assignment.setChangeProposedTarget(null);
        assignment.setChangeRequestedOn(null);
        assignmentRepository.save(assignment);
        log.info("Sent targets for employee {} for acknowledgement", request.employeeUserId());
        return getEmployeeTargets(manager, request.employeeUserId(), request.financialYear());
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * The employee's targets as a view, used by manager, employee and HOD screens alike. The name, code,
     * role and manager are context for the header card; callers that do not know them pass null.
     */
    private PmsEmployeeTargetsResponse view(Optional<PmsGoalAssignmentEntity> assignment,
                                            PmsGoalTemplateEntity template, String organisationId,
                                            String employeeUserId, String financialYear, String employeeName,
                                            String empCode, String roleName, String managerName) {
        Map<UUID, PmsGoalAssignmentTargetEntity> saved = savedTargets(assignment);
        Map<UUID, List<PmsEmployeeTargetsResponse.History>> history =
                history(organisationId, employeeUserId, financialYear);
        Optional<PmsTargetRevisionEntity> pendingRevision = assignment.isEmpty() ? Optional.empty()
                : revisionRepository.findFirstByAssignmentIdAndStatusAndAcknowledgedOnIsNullOrderByRequestedOnDesc(
                        assignment.get().getId(), PmsRevisionStatus.APPROVED);
        List<PmsTargetRevisionRowEntity> revisionRows = pendingRevision
                .map(r -> revisionRowRepository.findByRevisionId(r.getId()).stream()
                        .filter(row -> !"returned".equals(row.getDecision())).toList())
                .orElse(List.of());
        Map<UUID, BigDecimal> revisedTargets = new HashMap<>();
        Map<UUID, BigDecimal> previousByKpi = new HashMap<>();
        revisionRows.forEach(r -> {
            revisedTargets.put(r.getKpi().getId(), r.getRevisedTarget());
            previousByKpi.put(r.getKpi().getId(), r.getPreviousTarget());
        });
        // Every change request on the sheet: the count per KPI, and the latest revised value once answered.
        Map<UUID, Integer> requestCount = new HashMap<>();
        Map<UUID, BigDecimal> answeredRevision = new HashMap<>();
        if (assignment.isPresent()) {
            for (PmsGoalChangeRequestEntity r : changeRequestRepository
                    .findByAssignmentIdOrderByRequestedOnAscRevisionNoAsc(assignment.get().getId())) {
                requestCount.merge(r.getKpi().getId(), 1, Integer::sum);
                if (r.getDecision() == PmsChangeDecision.REVISED && r.getFinalTarget() != null) {
                    answeredRevision.put(r.getKpi().getId(), r.getFinalTarget());
                }
            }
        }
        // What the employee reported: the current figure, else the half-year, else the full-year one.
        Map<UUID, PmsGoalProgressEntity> reported = new HashMap<>();
        if (assignment.isPresent()) {
            List<PmsProgressPeriod> order = List.of(PmsProgressPeriod.CURRENT, PmsProgressPeriod.H1,
                    PmsProgressPeriod.FY);
            for (PmsGoalProgressEntity p : progressRepository.findByAssignmentId(assignment.get().getId())) {
                if (p.getActual() == null || !order.contains(p.getPeriod())) {
                    continue;
                }
                PmsGoalProgressEntity current = reported.get(p.getKpi().getId());
                if (current == null || order.indexOf(p.getPeriod()) < order.indexOf(current.getPeriod())) {
                    reported.put(p.getKpi().getId(), p);
                }
            }
        }
        List<PmsEmployeeTargetsResponse.Kpi> kpis = templateKpiRepository
                .findByTemplateIdOrderByDisplayOrderAsc(template.getId()).stream()
                .map(row -> {
                    PmsGoalAssignmentTargetEntity t = saved.get(row.getKpi().getId());
                    return new PmsEmployeeTargetsResponse.Kpi(
                            row.getKpi().getId(),
                            row.getKra().getName(),
                            row.getKpi().getName(),
                            row.getKpi().getUnit(),
                            row.getKpi().getExpectedOutcome(),
                            row.getKpi().getEvidenceRequired(),
                            t != null ? t.getWeight() : row.getWeightage(),
                            t != null ? t.getTarget() : null,
                            t == null ? null : (t.getOriginalTarget() != null ? t.getOriginalTarget() : t.getTarget()),
                            requestCount.getOrDefault(row.getKpi().getId(), 0),
                            revisedTargets.containsKey(row.getKpi().getId())
                                    ? revisedTargets.get(row.getKpi().getId())
                                    : latestRevised(t, answeredRevision.get(row.getKpi().getId())),
                            previousByKpi.get(row.getKpi().getId()),
                            reported.get(row.getKpi().getId()) == null ? null
                                    : reported.get(row.getKpi().getId()).getActual(),
                            reported.get(row.getKpi().getId()) == null ? null
                                    : reported.get(row.getKpi().getId()).getStatus(),
                            row.getKpi().getTargetType() == null ? null : wire(row.getKpi().getTargetType()),
                            history.getOrDefault(row.getKpi().getId(), List.of()));
                })
                .toList();
        PmsGoalAssignmentEntity a = assignment.orElse(null);
        return new PmsEmployeeTargetsResponse(
                employeeUserId,
                financialYear,
                a == null || (a.getStatus() == PmsAssignmentStatus.DRAFT && a.getHodRemarks() == null
                        && saved.isEmpty()) ? NOT_STARTED : wire(a.getStatus()),
                template.getId(),
                template.getTemplateName(),
                kpis,
                a == null ? null : a.getChangeKpiId(),
                a == null ? null : a.getChangeReason(),
                a == null ? null : a.getChangeProposedTarget(),
                a == null ? null : a.getHodRemarks(),
                employeeName,
                empCode,
                roleName,
                managerName != null ? managerName : (a == null ? null : a.getManagerName()),
                a == null || a.getSentOn() == null ? null : a.getSentOn().toLocalDate(),
                acknowledgeBy(organisationId, financialYear),
                a == null ? null : a.getAcknowledgeComment(),
                revision(pendingRevision.orElse(null), revisionRows, acknowledgeBy(organisationId, financialYear)));
    }

    /**
     * The latest finalised revised target for a KPI: the manager's answer to the last change request that moved
     * it, or (when the target changed some other way, e.g. after an HOD return) the current target once it differs
     * from the original. Null while the KPI still has its original target. The original is never touched.
     */
    private static BigDecimal latestRevised(PmsGoalAssignmentTargetEntity t, BigDecimal answered) {
        if (answered != null) {
            return answered;
        }
        if (t != null && t.getOriginalTarget() != null && t.getTarget() != null
                && t.getTarget().compareTo(t.getOriginalTarget()) != 0) {
            return t.getTarget();
        }
        return null;
    }

    /**
     * Records the targets the employee currently sees as the baseline, for sheets sent before the baseline
     * was stored. Rows that already have one keep it.
     */
    private void snapshotSentTargets(PmsGoalAssignmentEntity assignment) {
        for (PmsGoalAssignmentTargetEntity t : targetRepository.findByAssignmentId(assignment.getId())) {
            if (t.getTarget() == null) {
                continue;
            }
            boolean changed = false;
            if (t.getSentTarget() == null) {
                t.setSentTarget(t.getTarget());
                changed = true;
            }
            if (t.getOriginalTarget() == null) {
                t.setOriginalTarget(t.getSentTarget());
                changed = true;
            }
            if (changed) {
                targetRepository.save(t);
            }
        }
    }

    /**
     * The manager revised a returned sheet and is resending it: keeps a record of every target that moved,
     * with the manager's remark, so screen 4.5 can show the employee the old and the revised value.
     * A revision still waiting from an earlier round is superseded.
     */
    private void recordManagerRevision(PmsUserPrincipal manager, PmsGoalAssignmentEntity assignment,
                                       PmsTargetsRequest request, Map<UUID, BigDecimal> previousTargets,
                                       String fallbackReason) {
        for (PmsTargetRevisionEntity pending : revisionRepository
                .findByAssignmentIdAndStatusAndAcknowledgedOnIsNull(assignment.getId(), PmsRevisionStatus.APPROVED)) {
            pending.setAcknowledgedOn(LocalDateTime.now());
            revisionRepository.save(pending);
        }
        Map<UUID, PmsKpiMasterEntity> kpis = new HashMap<>();
        targetRepository.findByAssignmentId(assignment.getId())
                .forEach(t -> kpis.put(t.getKpi().getId(), t.getKpi()));

        List<PmsTargetRevisionRowEntity> rows = new ArrayList<>();
        for (PmsTargetsRequest.Target t : request.targets()) {
            BigDecimal before = previousTargets.get(t.kpiId());
            if (t.target() == null || before == null || before.compareTo(t.target()) == 0) {
                continue;
            }
            String remark = blankToNull(t.remarks());
            rows.add(PmsTargetRevisionRowEntity.builder()
                    .kpi(kpis.get(t.kpiId()))
                    .previousTarget(before)
                    .revisedTarget(t.target())
                    .reason(remark != null ? remark : fallbackReason)
                    .build());
        }
        if (rows.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        PmsTargetRevisionEntity revision = revisionRepository.save(PmsTargetRevisionEntity.builder()
                .assignment(assignment)
                .status(PmsRevisionStatus.APPROVED)
                .source(PmsRevisionSource.MANAGER_REVISED)
                .requestedBy(manager.id())
                .requestedOn(now)
                .approvedBy(manager.fullName())
                .approvedOn(now)
                .build());
        for (PmsTargetRevisionRowEntity row : rows) {
            row.setRevision(revision);
            revisionRowRepository.save(row);
        }
        log.info("Manager {} revised {} targets of employee {}", manager.id(), rows.size(),
                assignment.getEmployeeUserId());
    }

    /**
     * The HOD decision on a whole goal sheet. The first KPI whose target moved from its original is named
     * on the row so the approval history can show "from -> to".
     */
    private void recordHodDecision(PmsUserPrincipal hod, PmsGoalAssignmentEntity a, String action, String summary,
                                   String remarks) {
        PmsGoalAssignmentTargetEntity first = null;
        int moved = 0;
        for (PmsGoalAssignmentTargetEntity t : targetRepository.findByAssignmentId(a.getId())) {
            if (t.getOriginalTarget() != null && t.getTarget() != null
                    && t.getTarget().compareTo(t.getOriginalTarget()) != 0) {
                moved++;
                if (first == null) {
                    first = t;
                }
            }
        }
        sheetHistory.record(a, PmsSheetHistory.GOAL_SHEET, null, action, hod.id(), hod.fullName(),
                PmsSheetHistory.HOD, first == null ? null : first.getKpi().getId(),
                first == null ? null : first.getKpi().getName(), first == null ? null : first.getKpi().getUnit(),
                first == null ? null : first.getOriginalTarget(), first == null ? null : first.getTarget(),
                Math.max(0, moved - 1), summary, remarks);
    }

    /** Last day to acknowledge: the end of the employee-acknowledgement stage of the active cycle for the year. */
    private LocalDate acknowledgeBy(String organisationId, String financialYear) {
        Integer start = startYear(financialYear);
        if (start == null) {
            return null;
        }
        for (PmsCycleEntity cycle : cycleRepository.findByOrganisationIdAndStatus(organisationId,
                PmsCycleStatus.ACTIVE)) {
            LocalDate periodStart = cycle.getPeriodStart();
            int cycleYear = periodStart.getMonthValue() >= 4 ? periodStart.getYear() : periodStart.getYear() - 1;
            if (cycleYear != start) {
                continue;
            }
            for (PmsCycleStageEntity stage : cycleStageRepository.findByCycleId(cycle.getId())) {
                if (stage.getStage() == PmsCycleStageType.EMPLOYEE_ACKNOWLEDGEMENT && stage.getEndDate() != null) {
                    return stage.getEndDate();
                }
            }
        }
        return null;
    }

    private static PmsEmployeeTargetsResponse.Revision revision(PmsTargetRevisionEntity revision,
                                                                List<PmsTargetRevisionRowEntity> rows,
                                                                LocalDate acknowledgeBy) {
        if (revision == null || rows.isEmpty()) {
            return null;
        }
        PmsTargetRevisionRowEntity first = rows.get(0);
        PmsRevisionSource source = revision.getSource() == null ? PmsRevisionSource.HOD_APPROVED
                : revision.getSource();
        return new PmsEmployeeTargetsResponse.Revision(wire(source), first.getKpi().getId(),
                first.getRevisedTarget(), first.getReason(), revision.getApprovedBy(),
                revision.getApprovedOn() == null ? null : revision.getApprovedOn().toLocalDate(), acknowledgeBy);
    }

    /**
     * Screen 4.5: the employee acknowledges a HOD-approved revision. The revised targets replace the
     * sheet's targets from now on; the sheet stays approved.
     */
    private PmsEmployeeTargetsResponse acknowledgeRevision(PmsUserPrincipal employee, PmsGoalAssignmentEntity a,
                                                           String financialYear) {
        PmsTargetRevisionEntity revision = revisionRepository
                .findFirstByAssignmentIdAndStatusAndAcknowledgedOnIsNullOrderByRequestedOnDesc(a.getId(),
                        PmsRevisionStatus.APPROVED)
                .orElseThrow(() -> DomainException.unprocessable("There is no revised target waiting for you.",
                        "PMS_INVALID_GOAL_STATE"));
        Map<UUID, BigDecimal> revised = new HashMap<>();
        revisionRowRepository.findByRevisionId(revision.getId()).stream()
                .filter(r -> !"returned".equals(r.getDecision()))
                .forEach(r -> revised.put(r.getKpi().getId(), r.getRevisedTarget()));
        for (PmsGoalAssignmentTargetEntity t : targetRepository.findByAssignmentId(a.getId())) {
            BigDecimal next = revised.get(t.getKpi().getId());
            if (next != null) {
                t.setTarget(next);
                targetRepository.save(t);
            }
        }
        revision.setAcknowledgedOn(LocalDateTime.now());
        revisionRepository.save(revision);
        log.info("Employee {} acknowledged the revised targets for {}", employee.id(), financialYear);
        return getMyGoals(employee, financialYear);
    }

    /**
     * Targets and year-end actuals for up to three previous financial years, per KPI, newest first.
     * Years in which the employee had no goal sheet are skipped.
     */
    private Map<UUID, List<PmsEmployeeTargetsResponse.History>> history(String organisationId,
                                                                        String employeeUserId,
                                                                        String financialYear) {
        Map<UUID, List<PmsEmployeeTargetsResponse.History>> byKpi = new LinkedHashMap<>();
        Integer start = startYear(financialYear);
        if (start == null) {
            return byKpi;
        }
        for (int back = 1; back <= HISTORY_YEARS; back++) {
            String year = financialYearLabel(start - back);
            Optional<PmsGoalAssignmentEntity> previous = assignmentRepository
                    .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(organisationId, employeeUserId, year);
            if (previous.isEmpty()) {
                continue;
            }
            Map<UUID, BigDecimal> actuals = new HashMap<>();
            for (PmsGoalProgressEntity p : progressRepository.findByAssignmentId(previous.get().getId())) {
                // The year-end figure wins; otherwise the latest running value.
                if (p.getActual() != null
                        && (p.getPeriod() == PmsProgressPeriod.FY || !actuals.containsKey(p.getKpi().getId()))) {
                    actuals.put(p.getKpi().getId(), p.getActual());
                }
            }
            for (PmsGoalAssignmentTargetEntity t : targetRepository.findByAssignmentId(previous.get().getId())) {
                if (t.getTarget() == null) {
                    continue;
                }
                byKpi.computeIfAbsent(t.getKpi().getId(), k -> new ArrayList<>())
                        .add(new PmsEmployeeTargetsResponse.History(year, t.getTarget(),
                                actuals.get(t.getKpi().getId())));
            }
        }
        return byKpi;
    }

    /** {@code "2026-27"} gives 2026; anything else gives null. */
    static Integer startYear(String financialYear) {
        if (financialYear == null || financialYear.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(financialYear.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code 2025} gives {@code "2025-26"}. */
    static String financialYearLabel(int startYear) {
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    /** The goal state shown for a team member, given any assignment and transfer they have. */
    private static String teamStatus(PmsGoalAssignmentEntity assignment, PmsGoalTransferEntity transfer,
                                     boolean hasTargets) {
        if (transfer != null && transfer.getStatus() != PmsTransferStatus.SENT) {
            return "transferred_in";
        }
        // A template was assigned but no targets were saved yet.
        if (assignment == null
                || (assignment.getStatus() == PmsAssignmentStatus.DRAFT && assignment.getHodRemarks() == null
                && !hasTargets)) {
            return NOT_STARTED;
        }
        if (assignment.getStatus() == PmsAssignmentStatus.DRAFT && assignment.getHodRemarks() != null) {
            return "returned_by_hod";
        }
        return wire(assignment.getStatus());
    }

    /** The active template of a role in the year (null when none); one lookup per distinct role. */
    private PmsGoalTemplateEntity activeTemplateOfRole(String organisationId, String designationId,
                                                       String financialYear,
                                                       Map<String, PmsGoalTemplateEntity> cache) {
        if (designationId == null) {
            return null;
        }
        if (!cache.containsKey(designationId)) {
            cache.put(designationId, templateRepository
                    .findFirstByOrganisationIdAndRoleIdAndFinancialYearAndStatus(organisationId, designationId,
                            financialYear, PmsTemplateStatus.ACTIVE)
                    .orElse(null));
        }
        return cache.get(designationId);
    }

    private PmsGoalAssignmentEntity requireAssignment(String organisationId, String employeeUserId,
                                                      String financialYear) {
        return assignmentRepository
                .findByOrganisationIdAndEmployeeUserIdAndFinancialYear(organisationId, employeeUserId, financialYear)
                .orElseThrow(() -> DomainException.notFound("No goals have been set for " + financialYear,
                        "PMS_GOALS_NOT_FOUND"));
    }

    private static void requireStatus(PmsGoalAssignmentEntity a, PmsAssignmentStatus expected, String message) {
        if (a.getStatus() != expected) {
            throw DomainException.unprocessable(message, "PMS_INVALID_GOAL_STATE");
        }
    }

    private PmsGoalTemplateEntity requireTemplateById(PmsGoalAssignmentEntity a) {
        return templateRepository.findById(a.getTemplateId())
                .orElseThrow(() -> DomainException.notFound("Goal template not found", "PMS_TEMPLATE_NOT_FOUND"));
    }

    /** Only the caller's own direct reports may be read or changed. */
    private IamEmployee requireTeamMember(PmsUserPrincipal manager, String employeeUserId) {
        return iam.directReports(manager.organisationId(), manager.id()).stream()
                .filter(e -> employeeUserId.equals(e.userId()))
                .findFirst()
                .orElseThrow(() -> new DomainException("This employee is not in your team.",
                        "PMS_NOT_IN_TEAM", HttpStatus.FORBIDDEN));
    }

    private PmsGoalTemplateEntity requireTemplate(PmsUserPrincipal manager, IamEmployee employee,
                                                  String financialYear) {
        if (employee.designationId() == null) {
            throw DomainException.unprocessable("This employee has no designation, so no template applies.",
                    "PMS_TEMPLATE_NOT_FOUND");
        }
        return templateRepository.findFirstByOrganisationIdAndRoleIdAndFinancialYearAndStatus(
                        manager.organisationId(), employee.designationId(), financialYear, PmsTemplateStatus.ACTIVE)
                .orElseThrow(() -> DomainException.notFound(
                        "No active goal template for this role in " + financialYear, "PMS_TEMPLATE_NOT_FOUND"));
    }

    /** The KPIs the employee's template offers, keyed by id. */
    private Map<UUID, PmsKpiMasterEntity> allowedKpis(PmsGoalTemplateEntity template) {
        Map<UUID, PmsKpiMasterEntity> allowed = new HashMap<>();
        for (PmsGoalTemplateKpiEntity row : templateKpiRepository
                .findByTemplateIdOrderByDisplayOrderAsc(template.getId())) {
            allowed.put(row.getKpi().getId(), row.getKpi());
        }
        return allowed;
    }

    private static void checkKpisBelong(PmsTargetsRequest request, Map<UUID, PmsKpiMasterEntity> allowed) {
        for (PmsTargetsRequest.Target t : request.targets()) {
            if (!allowed.containsKey(t.kpiId())) {
                throw DomainException.unprocessable("kpi_id " + t.kpiId()
                        + " is not part of this employee's goal template", "PMS_KPI_NOT_IN_TEMPLATE");
            }
        }
    }

    private Map<UUID, PmsGoalAssignmentTargetEntity> savedTargets(Optional<PmsGoalAssignmentEntity> assignment) {
        Map<UUID, PmsGoalAssignmentTargetEntity> saved = new HashMap<>();
        assignment.ifPresent(a -> {
            for (PmsGoalAssignmentTargetEntity t : targetRepository.findByAssignmentId(a.getId())) {
                saved.put(t.getKpi().getId(), t);
            }
        });
        return saved;
    }

    private static String fullName(IamEmployee e) {
        return ((e.firstName() == null ? "" : e.firstName()) + " "
                + (e.lastName() == null ? "" : e.lastName())).trim();
    }

    private static PmsTeamMemberResponse toTeamMember(IamEmployee e, String status,
                                                      PmsGoalTemplateEntity template, boolean assigned) {
        return new PmsTeamMemberResponse(e.userId(), e.empCode(), fullName(e), e.workEmail(), e.designationId(),
                e.designationName(), status, template == null ? null : template.getId(),
                template == null ? null : template.getTemplateName(), assigned);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
