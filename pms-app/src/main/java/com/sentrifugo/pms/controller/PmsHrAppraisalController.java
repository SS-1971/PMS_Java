package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.PmsHrAppraisalResponse;
import com.sentrifugo.pms.model.PmsHrAppraisalSaveRequest;
import com.sentrifugo.pms.model.PmsHrQueueRow;
import com.sentrifugo.pms.model.PmsSendToCalibrationRequest;
import com.sentrifugo.pms.service.PmsHrAppraisalService;
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
import java.util.Map;

/** Corporate HR competency rating (screens 1.7 - 1.8). */
@RestController
@RequestMapping("${pms.api.base.path}/pms-hr-appraisal")
@RequiredArgsConstructor
public class PmsHrAppraisalController {

    private static final String MODULE = "performance_management";
    private static final String ACTION = "rating_normalization";

    private final PmsHrAppraisalService service;

    @GetMapping("/get/queue")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "HR competency rating queue (screen 1.7)",
            description = "Employees whose manager appraisal is in. Optional department_id, status "
                    + "(pending, draft, rated, all) and search (name or employee code) filters.")
    public ResponseEntity<ApiResponse<List<PmsHrQueueRow>>> queue(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear,
            @RequestParam(name = "department_id", required = false) String departmentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search) {
        List<PmsHrQueueRow> rows = service.queue(user, financialYear, departmentId, status, search);
        return ResponseEntity.ok(ApiResponse.ok(rows, "Found " + rows.size() + " employees"));
    }

    @GetMapping("/get/employee")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "HR competency rating of one employee (screen 1.8)",
            description = "409 PMS_MANAGER_APPRAISAL_PENDING until the manager has submitted the appraisal.")
    public ResponseEntity<ApiResponse<PmsHrAppraisalResponse>> employee(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.detail(user, employeeUserId, financialYear),
                "Appraisal loaded"));
    }

    @PutMapping("/update/hr-ratings")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Save draft (screen 1.8)", description = "Ratings may be left empty in a draft.")
    public ResponseEntity<ApiResponse<PmsHrAppraisalResponse>> save(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsHrAppraisalSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.save(user, request), "Draft saved"));
    }

    @PostMapping("/submit/hr-ratings")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Submit HR ratings (screen 1.8)",
            description = "422 PMS_HR_RATING_INCOMPLETE unless every HR competency is rated 1-5.")
    public ResponseEntity<ApiResponse<PmsHrAppraisalResponse>> submit(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsHrAppraisalSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.submit(user, request), "HR ratings submitted"));
    }

    @PostMapping("/send/calibration")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Send rated employees to calibration (screen 1.7)",
            description = "409 PMS_HR_RATING_PENDING for any employee HR has not submitted ratings for.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> send(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsSendToCalibrationRequest request) {
        int sent = service.sendToCalibration(user, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("sent", sent), sent + " sent to calibration"));
    }
}
