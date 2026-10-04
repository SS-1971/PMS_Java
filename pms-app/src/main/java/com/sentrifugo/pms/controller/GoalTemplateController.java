package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.goaltemplate.GoalTemplateDto;
import com.sentrifugo.pms.model.goaltemplate.GoalTemplateDtos;
import com.sentrifugo.pms.service.GoalTemplateService;
import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints 16–22 (PMS Configuration contract): goal templates (screens 2.1–2.4). */
@RestController
@RequestMapping("/pms/goal-templates")
public class GoalTemplateController {

    private static final Logger log = LoggerFactory.getLogger(GoalTemplateController.class);

    private final GoalTemplateService service;

    public GoalTemplateController(GoalTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List goal templates",
            description = "Retrieves the authenticated organisation's goal templates, optionally filtered by financial year, department and name search.")
    public ResponseEntity<ApiResponse<List<GoalTemplateDtos.Summary>>> list(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "financial_year", required = false) Integer financialYear,
            @RequestParam(name = "department_id", required = false) String departmentId,
            @RequestParam(required = false) String search) {
        log.info("Fetching goal templates for organisation: {}", user.organisationId());
        List<GoalTemplateDtos.Summary> templates =
                service.list(user.organisationId(), financialYear, departmentId, search);
        return ResponseEntity.ok(ApiResponse.ok(templates,
                String.format("Found %d goal templates", templates.size())));
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Get a goal template",
            description = "Retrieves one goal template with its KRAs, KPIs, competencies and resolved department/designation names.")
    public ResponseEntity<ApiResponse<GoalTemplateDto>> get(@AuthenticationPrincipal PmsUserPrincipal user,
                                                            @PathVariable UUID templateId) {
        log.info("Fetching goal template: {}", templateId);
        GoalTemplateDto template = service.get(user.organisationId(), templateId);
        return ResponseEntity.ok(ApiResponse.ok(template, "Goal template retrieved successfully"));
    }

    @PostMapping
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Create a goal template",
            description = "Creates a goal template; drafts may be partial, any other status must have complete KRA/KPI/competency weights.")
    public ResponseEntity<ApiResponse<GoalTemplateDto>> create(@AuthenticationPrincipal PmsUserPrincipal user,
                                                               @Valid @RequestBody GoalTemplateDtos.UpsertRequest body) {
        log.info("Creating goal template for organisation: {}", user.organisationId());
        GoalTemplateDto template = service.create(user.organisationId(), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(template, "Goal template created successfully"));
    }

    @PutMapping("/{templateId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Update a goal template",
            description = "Replaces a goal template's basic details, KRAs/KPIs and competencies.")
    public ResponseEntity<ApiResponse<GoalTemplateDto>> update(@AuthenticationPrincipal PmsUserPrincipal user,
                                                               @PathVariable UUID templateId,
                                                               @Valid @RequestBody GoalTemplateDtos.UpsertRequest body) {
        log.info("Updating goal template: {}", templateId);
        GoalTemplateDto template = service.update(user.organisationId(), templateId, body);
        return ResponseEntity.ok(ApiResponse.ok(template, "Goal template updated successfully"));
    }

    @PatchMapping("/{templateId}/status")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Change goal template status",
            description = "Moves a goal template between draft, active and inactive; activation requires a complete template.")
    public ResponseEntity<ApiResponse<GoalTemplateDto>> setStatus(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                  @PathVariable UUID templateId,
                                                                  @Valid @RequestBody GoalTemplateDtos.StatusRequest body) {
        log.info("Changing status of goal template: {}", templateId);
        GoalTemplateDto template = service.setStatus(user.organisationId(), templateId, body.status());
        return ResponseEntity.ok(ApiResponse.ok(template, "Goal template status updated successfully"));
    }

    @PostMapping("/{templateId}/duplicate")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Duplicate a goal template",
            description = "Creates a draft copy (named \"... (Copy)\") of an existing goal template, including its KRAs, KPIs and competencies.")
    public ResponseEntity<ApiResponse<GoalTemplateDto>> duplicate(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                  @PathVariable UUID templateId) {
        log.info("Duplicating goal template: {}", templateId);
        GoalTemplateDto template = service.duplicate(user.organisationId(), templateId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(template, "Goal template duplicated successfully"));
    }

    @DeleteMapping("/{templateId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Delete a goal template",
            description = "Soft-deletes a goal template of the authenticated organisation.")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal PmsUserPrincipal user,
                                                    @PathVariable UUID templateId) {
        log.info("Deleting goal template: {}", templateId);
        service.delete(user.organisationId(), templateId);
        return ResponseEntity.ok(ApiResponse.ok("Goal template deleted successfully"));
    }
}
