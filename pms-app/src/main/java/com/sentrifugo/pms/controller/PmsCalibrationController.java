package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.PmsCalibrationPublishRequest;
import com.sentrifugo.pms.model.PmsCalibrationResponse;
import com.sentrifugo.pms.model.PmsCalibrationSaveRequest;
import com.sentrifugo.pms.service.PmsCalibrationService;
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

/** Calibration &amp; normalization and publish (screen 1.9). */
@RestController
@RequestMapping("${pms.api.base.path}/pms-calibration")
@RequiredArgsConstructor
public class PmsCalibrationController {

    private static final String MODULE = "performance_management";
    private static final String ACTION = "rating_normalization";

    private final PmsCalibrationService service;

    @GetMapping("/get/calibration")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Calibration (screen 1.9)",
            description = "Rating distribution against the guideline and HR's targets, plus the employees "
                    + "sent from screen 1.7.")
    public ResponseEntity<ApiResponse<PmsCalibrationResponse>> get(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(user, financialYear), "Calibration loaded"));
    }

    @PutMapping("/update/calibration")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Save calibration (screen 1.9)",
            description = "Stores HR's normalization targets and each calibrated rating. Does not publish.")
    public ResponseEntity<ApiResponse<PmsCalibrationResponse>> save(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsCalibrationSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.save(user, request), "Calibration saved"));
    }

    @PostMapping("/publish/calibration")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Approve and publish ratings (screen 1.9)",
            description = "422 PMS_CALIBRATION_TOTAL unless HR's targets total 100%; 422 PMS_CALIBRATION_INVALID "
                    + "when a changed rating has no reason.")
    public ResponseEntity<ApiResponse<PmsCalibrationResponse>> publish(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsCalibrationPublishRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.publish(user, request.financialYear()),
                "Ratings published"));
    }
}
