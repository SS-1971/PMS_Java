package com.sentrifugo.pms.controller;

import com.sentrifugo.pms.model.PmsTransferNoticeResponse;
import com.sentrifugo.pms.model.PmsTransferRecordRequest;
import com.sentrifugo.pms.model.PmsTransferSheetRequest;
import com.sentrifugo.pms.model.PmsTransferSheetResponse;
import com.sentrifugo.pms.service.PmsGoalTransferService;
import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

/** Employees transferred into a manager team mid-year (screens 3.6 banner and 3.8). */
@RestController
@RequestMapping("${pms.api.base.path}/pms-goal-transfer")
@RequiredArgsConstructor
public class PmsGoalTransferController {

    private static final String MODULE = "performance_management";
    private static final String ACTION_TEAM_GOALS = "team_goals";
    private static final String ACTION_PMS_CYCLE = "pms_cycle";

    private final PmsGoalTransferService service;

    @GetMapping("/get/notices")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Transfer notices (screen 3.6)",
            description = "Employees who moved into the caller's team and still need period 2 KRAs.")
    public ResponseEntity<ApiResponse<List<PmsTransferNoticeResponse>>> notices(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsTransferNoticeResponse> notices = service.notices(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(notices, "Found " + notices.size() + " transfers"));
    }

    @GetMapping("/get/transfer-sheet")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Transferred employee goal sheet (screen 3.8)",
            description = "Frozen period 1 KRAs plus the editable period 2 KRAs, seeded from the role template.")
    public ResponseEntity<ApiResponse<PmsTransferSheetResponse>> sheet(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.sheet(user, employeeUserId, financialYear),
                "Transfer sheet loaded"));
    }

    @PutMapping("/update/transfer-sheet")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Save draft (screen 3.8)", description = "Replaces the period 2 KRAs. Drafts may be incomplete.")
    public ResponseEntity<ApiResponse<PmsTransferSheetResponse>> saveDraft(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTransferSheetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveDraft(user, request), "Draft saved"));
    }

    @PostMapping("/send/transfer-sheet")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_GOALS)
    @Operation(summary = "Send to employee (screen 3.8)",
            description = "Refuses with 422 PMS_TRANSFER_INVALID unless period 2 weightage is 100, every target is "
                    + "set and a reason and remarks are given.")
    public ResponseEntity<ApiResponse<PmsTransferSheetResponse>> send(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTransferSheetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.send(user, request),
                "Goal sheet sent to the employee for acknowledgement"));
    }

    @PostMapping("/create/transfer")
    @RequirePermission(module = MODULE, action = ACTION_PMS_CYCLE)
    @Operation(summary = "Record a transfer (HR)",
            description = "Records that an employee moved into a manager team, with the earlier KRAs frozen as "
                    + "period 1. The employee must already report to the new manager in the employee directory.")
    public ResponseEntity<ApiResponse<PmsTransferNoticeResponse>> record(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsTransferRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.record(user, request), "Transfer recorded"));
    }
}
