package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.db.dto.PmsCycleApplicabilityDto;
import com.sentrifugo.pms.model.cycle.CycleDtos;
import com.sentrifugo.pms.service.CycleService;
import com.sentrifugo.pms.utils.CycleCsvExporter;
import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints 1–9 (PMS Cycle contract): appraisal cycles (screens 1.1–1.6). */
@RestController
@RequestMapping("/pms/cycles")
public class CycleController {

    private static final Logger log = LoggerFactory.getLogger(CycleController.class);

    private final CycleService service;
    private final CycleCsvExporter csvExporter;

    public CycleController(CycleService service, CycleCsvExporter csvExporter) {
        this.service = service;
        this.csvExporter = csvExporter;
    }

    @GetMapping
    @Operation(summary = "List PMS cycles",
            description = "Retrieves a page of the organisation's appraisal cycles, filtered by search text, financial year, type, plant and status, together with status summary counts.")
    public ResponseEntity<ApiResponse<CycleDtos.ListResponse>> list(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @RequestParam(name = "plant_id", required = false) String plantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Fetching PMS cycles for organisation: {}", user.organisationId());
        CycleDtos.ListResponse cycles =
                service.list(user.organisationId(), search, year, type, plantId, status, skip, limit);
        return ResponseEntity.ok(ApiResponse.ok(cycles,
                String.format("Found %d PMS cycles", cycles.total())));
    }

    /**
     * File download: intentionally returns the raw CSV bytes rather than an
     * {@code ApiResponse} envelope, which cannot carry a file body.
     */
    @GetMapping("/export")
    @Operation(summary = "Export PMS cycles as CSV",
            description = "Downloads the organisation's appraisal cycles matching the given filters as a CSV file (pms-cycles.csv).")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal PmsUserPrincipal user,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(name = "plant_id", required = false) String plantId,
                                         @RequestParam(required = false) String status) {
        log.info("Exporting PMS cycles for organisation: {}", user.organisationId());
        byte[] csv = csvExporter.toCsv(
                service.listForExport(user.organisationId(), search, year, type, plantId, status));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("pms-cycles.csv").build());
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @PostMapping("/eligible-employees/preview")
    @Operation(summary = "Preview eligible employees",
            description = "Asks IAM which employees the given applicability rules would cover, with counts excluded for probation, notice period and minimum service.")
    public ResponseEntity<ApiResponse<CycleDtos.EligibilityPreview>> previewEligibility(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @Valid @RequestBody PmsCycleApplicabilityDto body) {
        log.info("Previewing eligible employees for organisation: {}", user.organisationId());
        CycleDtos.EligibilityPreview preview = service.previewEligibility(user.organisationId(), body);
        return ResponseEntity.ok(ApiResponse.ok(preview, "Eligible employees preview retrieved successfully"));
    }

    @GetMapping("/{cycleId}")
    @Operation(summary = "Get a PMS cycle",
            description = "Retrieves one appraisal cycle with its basic details, stages, applicability and finalize settings.")
    public ResponseEntity<ApiResponse<CycleDtos.CycleDto>> get(@AuthenticationPrincipal PmsUserPrincipal user,
                                                               @PathVariable UUID cycleId) {
        log.info("Fetching PMS cycle: {}", cycleId);
        CycleDtos.CycleDto cycle = service.get(user.organisationId(), cycleId);
        return ResponseEntity.ok(ApiResponse.ok(cycle, "PMS cycle retrieved successfully"));
    }

    @PostMapping
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Create a PMS cycle",
            description = "Creates a draft appraisal cycle with a generated cycle code, its stages, applicability and finalize settings.")
    public ResponseEntity<ApiResponse<CycleDtos.CycleDto>> create(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                  @Valid @RequestBody CycleDtos.UpsertRequest body) {
        log.info("Creating PMS cycle for organisation: {}", user.organisationId());
        CycleDtos.CycleDto cycle = service.create(user.organisationId(), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(cycle, "PMS cycle created successfully"));
    }

    @PutMapping("/{cycleId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Update a PMS cycle",
            description = "Replaces the details, stages, applicability and finalize settings of a draft or active cycle.")
    public ResponseEntity<ApiResponse<CycleDtos.CycleDto>> update(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                  @PathVariable UUID cycleId,
                                                                  @Valid @RequestBody CycleDtos.UpsertRequest body) {
        log.info("Updating PMS cycle: {}", cycleId);
        CycleDtos.CycleDto cycle = service.update(user.organisationId(), cycleId, body);
        return ResponseEntity.ok(ApiResponse.ok(cycle, "PMS cycle updated successfully"));
    }

    @PostMapping("/{cycleId}/publish")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Publish a PMS cycle",
            description = "Validates a draft cycle is complete, activates it and records the per-audience notification counts.")
    public ResponseEntity<ApiResponse<CycleDtos.ActivationDto>> publish(
            @AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID cycleId) {
        log.info("Publishing PMS cycle: {}", cycleId);
        CycleDtos.ActivationDto activation = service.publish(user.organisationId(), cycleId);
        return ResponseEntity.ok(ApiResponse.ok(activation, "PMS cycle published successfully"));
    }

    @GetMapping("/{cycleId}/activation")
    @Operation(summary = "Get PMS cycle activation summary",
            description = "Retrieves the cycle together with the notification counts from its most recent publish.")
    public ResponseEntity<ApiResponse<CycleDtos.ActivationDto>> activation(
            @AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID cycleId) {
        log.info("Fetching activation summary for PMS cycle: {}", cycleId);
        CycleDtos.ActivationDto activation = service.activation(user.organisationId(), cycleId);
        return ResponseEntity.ok(ApiResponse.ok(activation, "PMS cycle activation retrieved successfully"));
    }

    @PostMapping("/{cycleId}/cancel")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Cancel a PMS cycle",
            description = "Cancels a draft or active appraisal cycle.")
    public ResponseEntity<ApiResponse<CycleDtos.CycleDto>> cancel(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                  @PathVariable UUID cycleId) {
        log.info("Cancelling PMS cycle: {}", cycleId);
        CycleDtos.CycleDto cycle = service.cancel(user.organisationId(), cycleId);
        return ResponseEntity.ok(ApiResponse.ok(cycle, "PMS cycle cancelled successfully"));
    }
}
