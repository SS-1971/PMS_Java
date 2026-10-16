package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.PmsApprovalWorkflowRequest;
import com.sentrifugo.pms.model.PmsApprovalWorkflowResponse;
import com.sentrifugo.pms.service.PmsApprovalWorkflowService;
import com.sentrifugo.pms.utils.PmsPrincipals;
import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Approval Workflow master (Configuration): the approver chains a cycle can use (screen 1.5). */
@RestController
@RequestMapping("${pms.api.base.path}/pms-approval-workflow")
@RequiredArgsConstructor
public class PmsApprovalWorkflowController {

    private static final String MODULE = "performance_management";
    private static final String ACTION = "pms_cycle";

    private final PmsApprovalWorkflowService service;

    @GetMapping("/get/workflows")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "List approval workflows",
            description = "A Standard workflow is created the first time an organisation lists them.")
    public ResponseEntity<ApiResponse<List<PmsApprovalWorkflowResponse>>> list(
            @AuthenticationPrincipal PmsUserPrincipal user) {
        List<PmsApprovalWorkflowResponse> rows = service.list(PmsPrincipals.organisationId(user));
        return ResponseEntity.ok(ApiResponse.ok(rows, "Found " + rows.size() + " workflows"));
    }

    @PostMapping("/create/workflow")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Add an approval workflow")
    public ResponseEntity<ApiResponse<PmsApprovalWorkflowResponse>> create(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsApprovalWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                service.create(PmsPrincipals.organisationId(user), request), "Workflow created successfully"));
    }

    @PutMapping("/update/workflow/{workflowId}")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Edit an approval workflow")
    public ResponseEntity<ApiResponse<PmsApprovalWorkflowResponse>> update(
            @AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID workflowId,
            @Valid @RequestBody PmsApprovalWorkflowRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.update(PmsPrincipals.organisationId(user), workflowId, request), "Workflow updated"));
    }

    @DeleteMapping("/delete/workflow/{workflowId}")
    @RequirePermission(module = MODULE, action = ACTION)
    @Operation(summary = "Remove an approval workflow", description = "409 PMS_WORKFLOW_IN_USE when a cycle uses it.")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal PmsUserPrincipal user,
                                                    @PathVariable UUID workflowId) {
        service.delete(PmsPrincipals.organisationId(user), workflowId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Workflow removed"));
    }
}
