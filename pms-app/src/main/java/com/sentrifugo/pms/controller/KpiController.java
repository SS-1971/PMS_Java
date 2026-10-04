package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.db.dto.PmsKpiDto;
import com.sentrifugo.pms.db.dto.PmsKpiUpsertDto;
import com.sentrifugo.pms.service.KpiService;
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

/** Endpoints 5–8 (PMS Configuration contract): the KPI master. */
@RestController
@RequestMapping("/pms/kpis")
public class KpiController {

    private static final Logger log = LoggerFactory.getLogger(KpiController.class);

    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List KPIs",
            description = "Retrieves all Key Performance Indicators, with their parent KRA, for the authenticated organisation.")
    public ResponseEntity<ApiResponse<List<PmsKpiDto>>> list(@AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching KPIs for organisation: {}", user.organisationId());
        List<PmsKpiDto> kpis = service.list(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(kpis, String.format("Found %d KPIs", kpis.size())));
    }

    @PostMapping
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Create a KPI",
            description = "Creates a KPI under an existing KRA of the authenticated organisation; names are unique per KRA.")
    public ResponseEntity<ApiResponse<PmsKpiDto>> create(@AuthenticationPrincipal PmsUserPrincipal user,
                                                         @Valid @RequestBody PmsKpiUpsertDto body) {
        log.info("Creating KPI for organisation: {}", user.organisationId());
        PmsKpiDto kpi = service.create(user.organisationId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(kpi, "KPI created successfully"));
    }

    @PutMapping("/{kpiId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Update a KPI",
            description = "Replaces the editable fields of an existing KPI, including its parent KRA.")
    public ResponseEntity<ApiResponse<PmsKpiDto>> update(@AuthenticationPrincipal PmsUserPrincipal user,
                                                         @PathVariable UUID kpiId,
                                                         @Valid @RequestBody PmsKpiUpsertDto body) {
        log.info("Updating KPI: {}", kpiId);
        PmsKpiDto kpi = service.update(user.organisationId(), kpiId, body);
        return ResponseEntity.ok(ApiResponse.ok(kpi, "KPI updated successfully"));
    }

    @DeleteMapping("/{kpiId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Delete a KPI",
            description = "Soft-deletes a KPI; rejected while it is used in an active goal template.")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal PmsUserPrincipal user,
                                                    @PathVariable UUID kpiId) {
        log.info("Deleting KPI: {}", kpiId);
        service.delete(user.organisationId(), kpiId);
        return ResponseEntity.ok(ApiResponse.ok("KPI deleted successfully"));
    }
}
