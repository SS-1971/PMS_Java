package com.sentrifugo.pms.controller;

import com.sentrifugo.pms.model.PmsManagerAppraisalResponse;
import com.sentrifugo.pms.model.PmsManagerAppraisalSaveRequest;
import com.sentrifugo.pms.model.PmsSelfAppraisalRequest;
import com.sentrifugo.pms.model.PmsSelfAppraisalResponse;
import com.sentrifugo.pms.model.PmsTeamAppraisalResponse;
import com.sentrifugo.pms.service.PmsAppraisalService;
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

/** Team appraisal and manager rating (screens 3.13 - 3.14), and the employee's self appraisal. */
@RestController
@RequestMapping("${pms.api.base.path}/pms-appraisal")
@RequiredArgsConstructor
public class PmsAppraisalController {

    private static final String MODULE = "performance_management";
    private static final String ACTION_TEAM_APPRAISAL = "team_appraisal";
    private static final String ACTION_SELF_APPRAISAL = "self_appraisal";

    private final PmsAppraisalService service;

    @GetMapping("/get/team")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_APPRAISAL)
    @Operation(summary = "Team appraisal (screen 3.13)",
            description = "Each direct report's self appraisal and the caller's rating status.")
    public ResponseEntity<ApiResponse<List<PmsTeamAppraisalResponse>>> team(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year") String financialYear) {
        List<PmsTeamAppraisalResponse> rows = service.team(user, financialYear);
        return ResponseEntity.ok(ApiResponse.ok(rows, "Found " + rows.size() + " team members"));
    }

    @GetMapping("/get/manager-appraisal")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_APPRAISAL)
    @Operation(summary = "Manager appraisal of one employee (screen 3.14)",
            description = "409 PMS_SELF_APPRAISAL_PENDING until the employee has submitted their self appraisal.")
    public ResponseEntity<ApiResponse<PmsManagerAppraisalResponse>> manager(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "employee_user_id") String employeeUserId,
            @RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.manager(user, employeeUserId, financialYear),
                "Appraisal loaded"));
    }

    @PutMapping("/update/manager-appraisal")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_APPRAISAL)
    @Operation(summary = "Save draft (screen 3.14)", description = "Ratings may be left empty in a draft.")
    public ResponseEntity<ApiResponse<PmsManagerAppraisalResponse>> save(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsManagerAppraisalSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveManager(user, request), "Draft saved"));
    }

    @PostMapping("/submit/manager-appraisal")
    @RequirePermission(module = MODULE, action = ACTION_TEAM_APPRAISAL)
    @Operation(summary = "Submit to HOD (screen 3.14)",
            description = "Refuses with 422 PMS_APPRAISAL_INVALID unless every KRA and competency is rated, a "
                    + "recommended rating is chosen and wide gaps from the self rating are explained.")
    public ResponseEntity<ApiResponse<PmsManagerAppraisalResponse>> submit(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsManagerAppraisalSaveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.submitManager(user, request),
                "Appraisal submitted to the HOD"));
    }

    @GetMapping("/get/self-appraisal")
    @RequirePermission(module = MODULE, action = ACTION_SELF_APPRAISAL)
    @Operation(summary = "My self appraisal sheet (screen 4.6)",
            description = "KRAs with original and revised target, actual, self rating and evidence; competencies "
                    + "with type, self rating and comment. Needs HOD-approved goals.")
    public ResponseEntity<ApiResponse<com.sentrifugo.pms.model.PmsMySelfAppraisalResponse>> mySelf(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @org.springframework.web.bind.annotation.RequestParam(name = "financial_year") String financialYear) {
        return ResponseEntity.ok(ApiResponse.ok(service.mySelf(user, financialYear), "Self appraisal found"));
    }

    @PutMapping("/update/self-appraisal")
    @RequirePermission(module = MODULE, action = ACTION_SELF_APPRAISAL)
    @Operation(summary = "Save or submit the self appraisal",
            description = "submit false keeps a draft; submit true requires every KRA and competency rated.")
    public ResponseEntity<ApiResponse<PmsSelfAppraisalResponse>> self(
            @AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody PmsSelfAppraisalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveSelf(user, request),
                request.submit() ? "Self appraisal submitted" : "Draft saved"));
    }
}
