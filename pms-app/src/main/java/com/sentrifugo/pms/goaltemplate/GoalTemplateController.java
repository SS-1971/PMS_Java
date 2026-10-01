package com.sentrifugo.pms.goaltemplate;

import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints 16–22 (PMS Configuration contract): goal templates (screens 2.1–2.4). */
@RestController
@RequestMapping("/pms/goal-templates")
public class GoalTemplateController {

    private final GoalTemplateService service;

    public GoalTemplateController(GoalTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<GoalTemplateDtos.Summary> list(@AuthenticationPrincipal PmsUserPrincipal user,
                                               @RequestParam(name = "financial_year", required = false) Integer financialYear,
                                               @RequestParam(name = "department_id", required = false) String departmentId,
                                               @RequestParam(required = false) String search) {
        return service.list(user.organisationId(), financialYear, departmentId, search);
    }

    @GetMapping("/{templateId}")
    public GoalTemplateDto get(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID templateId) {
        return service.get(user.organisationId(), templateId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public GoalTemplateDto create(@AuthenticationPrincipal PmsUserPrincipal user,
                                  @Valid @RequestBody GoalTemplateDtos.UpsertRequest body) {
        return service.create(user.organisationId(), body);
    }

    @PutMapping("/{templateId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public GoalTemplateDto update(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID templateId,
                                  @Valid @RequestBody GoalTemplateDtos.UpsertRequest body) {
        return service.update(user.organisationId(), templateId, body);
    }

    @PatchMapping("/{templateId}/status")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public GoalTemplateDto setStatus(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID templateId,
                                     @Valid @RequestBody GoalTemplateDtos.StatusRequest body) {
        return service.setStatus(user.organisationId(), templateId, body.status());
    }

    @PostMapping("/{templateId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public GoalTemplateDto duplicate(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID templateId) {
        return service.duplicate(user.organisationId(), templateId);
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public void delete(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID templateId) {
        service.delete(user.organisationId(), templateId, user.id());
    }
}
