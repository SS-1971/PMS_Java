package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.db.dto.PmsCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsCompetencyUpsertDto;
import com.sentrifugo.pms.service.CompetencyService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints (PMS Configuration contract): the competency master. */
@RestController
@RequestMapping("/pms/competencies")
public class CompetencyController {

    private static final Logger log = LoggerFactory.getLogger(CompetencyController.class);

    private final CompetencyService service;

    public CompetencyController(CompetencyService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List competencies",
            description = "Retrieves all competencies defined for the authenticated organisation.")
    public ResponseEntity<ApiResponse<List<PmsCompetencyDto>>> list(
            @AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching competencies for organisation: {}", user.organisationId());
        List<PmsCompetencyDto> competencies = service.list(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(competencies,
                String.format("Found %d competencies", competencies.size())));
    }

    @PostMapping
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Create a competency",
            description = "Creates a competency in the authenticated organisation; names must be unique per organisation.")
    public ResponseEntity<ApiResponse<PmsCompetencyDto>> create(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                @Valid @RequestBody PmsCompetencyUpsertDto body) {
        log.info("Creating competency for organisation: {}", user.organisationId());
        PmsCompetencyDto competency = service.create(user.organisationId(), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(competency, "Competency created successfully"));
    }

    @PutMapping("/{competencyId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Update a competency",
            description = "Updates the name, category and enabled flag of an existing competency.")
    public ResponseEntity<ApiResponse<PmsCompetencyDto>> update(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                @PathVariable UUID competencyId,
                                                                @Valid @RequestBody PmsCompetencyUpsertDto body) {
        log.info("Updating competency: {}", competencyId);
        PmsCompetencyDto competency = service.update(user.organisationId(), competencyId, body);
        return ResponseEntity.ok(ApiResponse.ok(competency, "Competency updated successfully"));
    }

    @DeleteMapping("/{competencyId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Delete a competency",
            description = "Soft-deletes a competency; rejected while it is used in an active goal template.")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal PmsUserPrincipal user,
                                                    @PathVariable UUID competencyId) {
        log.info("Deleting competency: {}", competencyId);
        service.delete(user.organisationId(), competencyId);
        return ResponseEntity.ok(ApiResponse.ok("Competency deleted successfully"));
    }
}
