package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.PmsApprovalActionRequest;
import com.sentrifugo.pms.model.PmsApprovalItemResponse;
import com.sentrifugo.pms.model.PmsChangeRequest;
import com.sentrifugo.pms.model.PmsChangeRequestResponse;
import com.sentrifugo.pms.model.PmsEmployeeTargetsResponse;
import com.sentrifugo.pms.model.PmsBulkApproveRequest;
import com.sentrifugo.pms.model.PmsBulkApproveResponse;
import com.sentrifugo.pms.model.PmsGoalDecisionRequest;
import com.sentrifugo.pms.model.PmsHodActionRequest;
import com.sentrifugo.pms.model.PmsHodHistoryItemResponse;
import com.sentrifugo.pms.model.PmsHodQueueItemResponse;
import com.sentrifugo.pms.model.PmsHodReviewResponse;
import com.sentrifugo.pms.model.PmsHodTrailResponse;
import com.sentrifugo.pms.model.PmsSendToHodRequest;
import com.sentrifugo.pms.model.PmsTargetValidationResponse;
import com.sentrifugo.pms.model.PmsTargetsRequest;
import com.sentrifugo.pms.model.PmsTemplateAssignRequest;
import com.sentrifugo.pms.model.PmsTemplateAssignmentResponse;
import com.sentrifugo.pms.model.PmsTeamMemberResponse;
import com.sentrifugo.pms.service.PmsGoalAssignmentService;
import com.sentrifugo.pms.service.PmsHodApprovalService;
import com.sentrifugo.pms.service.PmsStageNoticeService;
import com.sentrifugo.pms.model.PmsStageNoticeResponse;
import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Goal assignment by the manager (screens 3.1 - 3.5). */
@RestController
@RequestMapping("${pms.api.base.path}/pms-goal-assignment")
@RequiredArgsConstructor
public class PmsGoalAssignmentController {

    private static final String MODULE = "performance_management";
    // Codes below match IAM's seeded permission catalog (performance_management
    // module) exactly — see scripts/seed_permissions.py in Sentrifugo-IAM-Admin-BE.
    private static final String ACTION_TEAM_GOALS = "team_goals";
    private static final String ACTION_MY_GOALS = "my_goals";
    private static final String ACTION_GOAL_APPROVAL = "goal_approval";

    private final PmsGoalAssignmentService service;
    private final PmsHodApprovalService hodService;
    private final PmsStageNoticeService stageNoticeService;

    @GetMapping("/get/team")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "My team (screen 3.1)",
            description = "The caller's direct reports, from IAM, with their goal status for the financial year.")
    public ResponseEntity<ApiResponse<List<PmsTeamMemberResponse>>> getTeam(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsTeamMemberResponse> team = service.getTeam(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(team, "Found " + team.size() + " team members"));
    }

    @GetMapping("/get/employee-targets")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Employee targets (screens 3.2 and 3.3)",
            description = "The employee's template KPIs with the weights and targets saved so far.")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> getEmployeeTargets(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        PmsEmployeeTargetsResponse targets = service.getEmployeeTargets(user, employeeUserId, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(targets, "Targets loaded"));
    }

    @PutMapping("/update/employee-targets")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Save draft (screen 3.3)",
            description = "Stores the weights and targets as given. Incomplete drafts are allowed.")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> saveDraft(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTargetsRequest request) {
        PmsEmployeeTargetsResponse saved = service.saveDraft(user, request);
        return ResponseEntity.ok(ApiResponse.ok(saved, "Draft saved"));
    }

    @PostMapping("/send")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Save and send for acknowledgement (screen 3.5)",
            description = "Refuses with 422 PMS_TARGETS_INVALID unless the targets validate; otherwise saves them "
                    + "and sends them to the employee.")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> send(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTargetsRequest request) {
        PmsEmployeeTargetsResponse sent = service.send(user, request);
        return ResponseEntity.ok(ApiResponse.ok(sent, "Goals sent to the employee for acknowledgement"));
    }

    @GetMapping("/get/my-change-requests")
    @RequirePermission(module = MODULE, action = ACTION_MY_GOALS)
    @Operation(summary = "My change requests (screen 4.2)",
            description = "Every change request the signed-in employee made for the year, with the manager's "
                    + "answer, oldest first. Kept permanently; revision_no counts the requests per KPI.")
    public ResponseEntity<ApiResponse<List<PmsChangeRequestResponse>>> myChangeRequests(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.myChangeRequests(user, financialYear), "Change requests"));
    }

    @GetMapping("/get/change-requests")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "A report's change requests (screen 3.7)",
            description = "The change requests one direct report made for the year, with the manager's answers.")
    public ResponseEntity<ApiResponse<List<PmsChangeRequestResponse>>> changeRequests(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.changeRequestsOfReport(user, employeeUserId, financialYear),
                "Change requests"));
    }

    @PostMapping("/assign-template")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Create and assign the role template (screen 3.2)",
            description = "Gives the template named by template_id to every direct report with the role "
                    + "(designation_id) who has none yet; employees with a goal sheet keep theirs. 404 "
                    + "PMS_TEMPLATE_NOT_FOUND for an unknown template; 422 PMS_TEMPLATE_ROLE_MISMATCH when it is not "
                    + "the role's active template for the year; 422 PMS_ROLE_NOT_IN_TEAM when no report has the role.")
    public ResponseEntity<ApiResponse<PmsTemplateAssignmentResponse>> assignTemplate(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTemplateAssignRequest request) {
        PmsTemplateAssignmentResponse result = service.assignTemplate(user, request);
        return ResponseEntity.ok(ApiResponse.ok(result, result.assigned() + " employees assigned the template"));
    }

    @PostMapping("/send-to-hod")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Send acknowledged goal sheets to the HOD (screen 3.6)",
            description = "Moves each listed sheet from acknowledged to with_hod. Refuses with 422 "
                    + "PMS_INVALID_GOAL_STATE if any sheet is not acknowledged. Returns the ids that were sent.")
    public ResponseEntity<ApiResponse<List<String>>> sendToHod(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsSendToHodRequest request) {
        List<String> sent = service.sendToHod(user, request);
        return ResponseEntity.ok(ApiResponse.ok(sent, sent.size() + " goal sheets sent to the HOD"));
    }

    // ── Employee (4.1 - 4.3) ─────────────────────────────────────────────────

    @GetMapping("/get/my-goals")
    @RequirePermission(module = MODULE, action = ACTION_MY_GOALS)
    @Operation(summary = "My goals (screen 4.1)", description = "The signed-in employee's own targets for the year.")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> myGoals(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.getMyGoals(user, financialYear), "Goals loaded"));
    }

    @PostMapping("/acknowledge")
    @RequirePermission(module = MODULE, action = ACTION_MY_GOALS)
    @Operation(summary = "Acknowledge goals (screen 4.3)",
            description = "The employee accepts the goals that were sent to them.")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> acknowledge(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsGoalDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.acknowledge(user, request), "Goals acknowledged"));
    }

    @PostMapping("/request-change")
    @RequirePermission(module = MODULE, action = ACTION_MY_GOALS)
    @Operation(summary = "Request a change (screen 4.2)",
            description = "The employee asks the manager to revise one target.")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> requestChange(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.requestChange(user, request), "Change requested"));
    }

    // ── HOD (5.1 - 5.2) ──────────────────────────────────────────────────────

    @GetMapping("/get/approvals")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Goals awaiting HOD approval (screen 5.1)")
    public ResponseEntity<ApiResponse<List<PmsApprovalItemResponse>>> approvals(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsApprovalItemResponse> queue = service.approvalQueue(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(queue, "Found " + queue.size() + " sheets awaiting approval"));
    }

    @PostMapping("/approve")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Approve goals (screen 5.2)")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> approve(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsApprovalActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.approve(user, request), "Goals approved"));
    }

    @PostMapping("/return")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Return goals to the manager (screen 5.2)")
    public ResponseEntity<ApiResponse<PmsEmployeeTargetsResponse>> returnToManager(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsApprovalActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.returnToManager(user, request), "Goals returned to manager"));
    }

    // ── Stage windows: the bell and the "open until" banner ──────────────────

    @GetMapping("/get/stage-notices")
    @Operation(summary = "Stage windows that concern me",
            description = "Goal setting for a manager, employee acknowledgement for an employee, HOD approval for an "
                    + "HOD: start and end dates from the active cycle timeline. `notify` says whether the bell "
                    + "should announce it; the banner shows the end date whenever the stage is returned.")
    public ResponseEntity<ApiResponse<List<PmsStageNoticeResponse>>> stageNotices(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(stageNoticeService.notices(user, financialYear), "Notices found"));
    }

    // ── HOD approvals: goal sheets and target revisions ─────────────────────

    @GetMapping("/get/hod-queue")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Goal sheets and target revisions waiting for the HOD")
    public ResponseEntity<ApiResponse<List<PmsHodQueueItemResponse>>> hodQueue(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsHodQueueItemResponse> queue = hodService.queue(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(queue, "Found " + queue.size() + " requests awaiting approval"));
    }

    @GetMapping("/get/hod-review")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Review one request: changed KRAs with who asked and why")
    public ResponseEntity<ApiResponse<PmsHodReviewResponse>> hodReview(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear,
            @RequestParam(name = "request_type", defaultValue = "goal_sheet") String requestType) {
        return ResponseEntity.ok(ApiResponse.ok(hodService.review(user, employeeUserId, financialYear, requestType),
                "Request found"));
    }

    @PostMapping("/hod/approve")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Approve a goal sheet, or selected KPIs of a target revision")
    public ResponseEntity<ApiResponse<PmsHodReviewResponse>> hodApprove(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsHodActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hodService.approve(user, request), "Approved"));
    }

    @PostMapping("/hod/return")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Return a goal sheet, or selected KPIs of a target revision, to the manager",
            description = "Remarks are required.")
    public ResponseEntity<ApiResponse<PmsHodReviewResponse>> hodReturn(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsHodActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hodService.returnToManager(user, request), "Returned to manager"));
    }

    @PostMapping("/hod/approve-selected")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Approve several requests at once",
            description = "Returns the outcome of every request; a failure does not stop the others.")
    public ResponseEntity<ApiResponse<PmsBulkApproveResponse>> hodApproveSelected(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsBulkApproveRequest request) {
        PmsBulkApproveResponse result = hodService.approveSelected(user, request);
        return ResponseEntity.ok(ApiResponse.ok(result,
                result.approved() + " approved, " + result.failed() + " failed"));
    }

    @GetMapping("/get/hod-history")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Approval history: decisions the HOD already made")
    public ResponseEntity<ApiResponse<List<PmsHodHistoryItemResponse>>> hodHistory(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear,
            @RequestParam(name = "request_type", required = false) String requestType,
            @RequestParam(name = "decision", required = false) String decision,
            @RequestParam(name = "search", required = false) String search) {
        List<PmsHodHistoryItemResponse> rows = hodService.history(user, financialYear, requestType, decision, search);
        return ResponseEntity.ok(ApiResponse.ok(rows, "Found " + rows.size() + " decisions"));
    }

    @GetMapping("/get/hod-trail")
    @RequirePermission(module = MODULE, action = ACTION_GOAL_APPROVAL)
    @Operation(summary = "Full trail of one employee: every request and step")
    public ResponseEntity<ApiResponse<PmsHodTrailResponse>> hodTrail(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(hodService.trail(user, employeeUserId, financialYear), "Trail found"));
    }

    @PostMapping("/validate")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Validate targets (screen 3.4)",
            description = "Checks weightage totals 100 and every KPI has a numeric target. Writes nothing.")
    public ResponseEntity<ApiResponse<PmsTargetValidationResponse>> validate(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTargetsRequest request) {
        PmsTargetValidationResponse result = service.validate(user, request);
        return ResponseEntity.ok(ApiResponse.ok(result, result.valid() ? "Validation passed"
                : "Validation failed"));
    }
}
