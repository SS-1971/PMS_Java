package com.sentrifugo.pms.controller;

import com.sentrifugo.pms.model.PmsProgressUpdateRequest;
import com.sentrifugo.pms.model.PmsTeamProgressResponse;
import com.sentrifugo.pms.service.PmsProgressService;
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

/** Team progress dashboard (screen 3.9) and the employee's entry of actuals. */
@RestController
@RequestMapping("${pms.api.base.path}/pms-progress")
@RequiredArgsConstructor
public class PmsProgressController {

    private static final String MODULE = "performance_management";
    private static final String ACTION_TEAM_PROGRESS = "team_progress";
    private static final String ACTION_PROGRESS = "progress";

    private final PmsProgressService service;

    @GetMapping("/get/team")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_PROGRESS)
    @Operation(summary = "Team progress (screen 3.9)",
            description = "KRAs on track, KRAs needing attention and achievement per direct report. Employees whose "
                    + "goals are not HOD approved are returned with locked = true.")
    public ResponseEntity<ApiResponse<List<PmsTeamProgressResponse>>> team(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsTeamProgressResponse> rows = service.teamProgress(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(rows, "Found " + rows.size() + " team members"));
    }

    @GetMapping("/get/my-progress")
    @RequirePermission(module = MODULE, action = ACTION_PROGRESS)
    @Operation(summary = "My progress sheet (screen 4.4)",
            description = "Original target, revised target, actual, status and evidence per KPI for one period "
                    + "(current, h1 or fy; default current). Needs HOD-approved goals.")
    public ResponseEntity<ApiResponse<com.sentrifugo.pms.model.PmsMyProgressResponse>> myProgress(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @org.springframework.web.bind.annotation.RequestParam(name = "financial_year") String financialYear,
            @org.springframework.web.bind.annotation.RequestParam(name = "period", defaultValue = "current")
            String period) {
        return ResponseEntity.ok(ApiResponse.ok(service.myProgress(user, financialYear, period), "Progress found"));
    }

    @PutMapping("/update/my-progress")
    @RequirePermission(module = MODULE, action = ACTION_PROGRESS)
    @Operation(summary = "Report actuals",
            description = "The signed-in employee reports actuals against their approved goals. period is one of "
                    + "current, h1, fy.")
    public ResponseEntity<ApiResponse<PmsProgressService.Saved>> update(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsProgressUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateMyProgress(user, request), "Progress saved"));
    }
}
