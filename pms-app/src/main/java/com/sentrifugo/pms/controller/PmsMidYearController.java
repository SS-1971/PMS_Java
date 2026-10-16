package com.sentrifugo.pms.controller;

import com.sentrifugo.pms.model.PmsMidYearReviewResponse;
import com.sentrifugo.pms.model.PmsMidYearRowResponse;
import com.sentrifugo.pms.model.PmsMidYearSubmitRequest;
import com.sentrifugo.pms.model.PmsRevisionRequestResponse;
import com.sentrifugo.pms.model.PmsRevisionSubmitRequest;
import com.sentrifugo.pms.service.PmsMidYearService;
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

/** The manager's mid-year review and target revision request (screens 3.10 - 3.12). */
@RestController
@RequestMapping("${pms.api.base.path}/pms-mid-year")
@RequiredArgsConstructor
public class PmsMidYearController {

    private static final String MODULE = "performance_management";
    private static final String ACTION_MID_YEAR = "mid_year_review";

    private final PmsMidYearService service;

    @GetMapping("/get/team")
    @RequirePermission(module = MODULE, action = ACTION_MID_YEAR)
    @Operation(summary = "Mid-year review, my team (screen 3.10)")
    public ResponseEntity<ApiResponse<List<PmsMidYearRowResponse>>> team(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsMidYearRowResponse> rows = service.team(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(rows, "Found " + rows.size() + " team members"));
    }

    @GetMapping("/get/review")
    @RequirePermission(module = MODULE, action = ACTION_MID_YEAR)
    @Operation(summary = "Mid-year review of one employee (screen 3.11)")
    public ResponseEntity<ApiResponse<PmsMidYearReviewResponse>> review(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.review(user, employeeUserId, financialYear),
                "Review loaded"));
    }

    @PostMapping("/submit/review")
    @RequirePermission(module = MODULE, action = ACTION_MID_YEAR)
    @Operation(summary = "Save the review decision (screen 3.11)",
            description = "revision_required false completes the review. true needs discussion notes and leaves it "
                    + "open until the revision request is sent.")
    public ResponseEntity<ApiResponse<PmsMidYearReviewResponse>> submit(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsMidYearSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.submit(user, request), "Mid-year review saved"));
    }

    @GetMapping("/get/revision-request")
    @RequirePermission(module = MODULE, action = ACTION_MID_YEAR)
    @Operation(summary = "Target revision request (screen 3.12)")
    public ResponseEntity<ApiResponse<PmsRevisionRequestResponse>> revisionRequest(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.revisionRequest(user, employeeUserId, financialYear),
                "Revision request loaded"));
    }

    @PostMapping("/submit/revision-request")
    @RequirePermission(module = MODULE, action = ACTION_MID_YEAR)
    @Operation(summary = "Send the target revision to the HOD (screen 3.12)",
            description = "Only changed targets are sent, each with a reason. KRAs and weightage are locked.")
    public ResponseEntity<ApiResponse<PmsRevisionRequestResponse>> submitRevision(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsRevisionSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.submitRevision(user, request),
                "Target revision sent to the HOD for approval"));
    }
}
