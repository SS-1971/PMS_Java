package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.model.lookup.DepartmentDto;
import com.sentrifugo.pms.model.lookup.DesignationDto;
import com.sentrifugo.pms.model.lookup.PlantDto;
import com.sentrifugo.pms.service.LookupService;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints 10–12 (PMS Cycle contract) / 9, 23 (PMS Configuration contract): dropdown lookups. */
@RestController
@RequestMapping("/pms/lookups")
public class LookupsController {

    private static final Logger log = LoggerFactory.getLogger(LookupsController.class);

    private final LookupService service;

    public LookupsController(LookupService service) {
        this.service = service;
    }

    @GetMapping("/plants")
    @Operation(summary = "List plants",
            description = "Retrieves the non-deleted plants (IAM business units) of the authenticated organisation, sorted by name.")
    public ResponseEntity<ApiResponse<List<PlantDto>>> plants(@AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching plants for organisation: {}", user.organisationId());
        List<PlantDto> plants = service.listPlants(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(plants, String.format("Found %d plants", plants.size())));
    }

    @GetMapping("/departments")
    @Operation(summary = "List departments",
            description = "Retrieves the non-deleted departments of the authenticated organisation, sorted by name.")
    public ResponseEntity<ApiResponse<List<DepartmentDto>>> departments(
            @AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching departments for organisation: {}", user.organisationId());
        List<DepartmentDto> departments = service.listDepartments(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(departments,
                String.format("Found %d departments", departments.size())));
    }

    @GetMapping("/designations")
    @Operation(summary = "List designations",
            description = "Retrieves the non-deleted designations of the authenticated organisation, optionally narrowed to one department.")
    public ResponseEntity<ApiResponse<List<DesignationDto>>> designations(
            @AuthenticationPrincipal PmsUserPrincipal user,
            @RequestParam(name = "department_id", required = false) String departmentId) {
        log.info("Fetching designations for organisation: {}", user.organisationId());
        List<DesignationDto> designations = service.listDesignations(user.organisationId(), departmentId);
        return ResponseEntity.ok(ApiResponse.ok(designations,
                String.format("Found %d designations", designations.size())));
    }

    @GetMapping("/kpi-units")
    @Operation(summary = "List KPI units",
            description = "Retrieves the fixed list of measurement units a KPI can be defined in.")
    public ResponseEntity<ApiResponse<List<String>>> kpiUnits() {
        List<String> units = service.listKpiUnits();
        return ResponseEntity.ok(ApiResponse.ok(units, String.format("Found %d KPI units", units.size())));
    }
}
