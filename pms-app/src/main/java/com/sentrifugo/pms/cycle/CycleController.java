package com.sentrifugo.pms.cycle;

import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Endpoints 1–9 (PMS Cycle contract): appraisal cycles (screens 1.1–1.6). */
@RestController
@RequestMapping("/pms/cycles")
public class CycleController {

    private final CycleService service;
    private final CycleCsvExporter csvExporter;

    public CycleController(CycleService service, CycleCsvExporter csvExporter) {
        this.service = service;
        this.csvExporter = csvExporter;
    }

    @GetMapping
    public CycleDtos.ListResponse list(@AuthenticationPrincipal PmsUserPrincipal user,
                                       @RequestParam(required = false) String search,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(name = "plant_id", required = false) String plantId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int skip,
                                       @RequestParam(defaultValue = "20") int limit) {
        return service.list(user.organisationId(), search, year, type, plantId, status, skip, limit);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal PmsUserPrincipal user,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(name = "plant_id", required = false) String plantId,
                                         @RequestParam(required = false) String status) {
        byte[] csv = csvExporter.toCsv(
                service.listForExport(user.organisationId(), search, year, type, plantId, status));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("pms-cycles.csv").build());
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @PostMapping("/eligible-employees/preview")
    public CycleDtos.EligibilityPreview previewEligibility(@AuthenticationPrincipal PmsUserPrincipal user,
                                                            @Valid @RequestBody CycleDtos.Applicability body) {
        return service.previewEligibility(user.organisationId(), body);
    }

    @GetMapping("/{cycleId}")
    public CycleDtos.CycleDto get(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID cycleId) {
        return service.get(user.organisationId(), cycleId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public CycleDtos.CycleDto create(@AuthenticationPrincipal PmsUserPrincipal user,
                                     @Valid @RequestBody CycleDtos.UpsertRequest body) {
        return service.create(user.organisationId(), body);
    }

    @PutMapping("/{cycleId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public CycleDtos.CycleDto update(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID cycleId,
                                     @Valid @RequestBody CycleDtos.UpsertRequest body) {
        return service.update(user.organisationId(), cycleId, body);
    }

    @PostMapping("/{cycleId}/publish")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public CycleDtos.ActivationDto publish(@AuthenticationPrincipal PmsUserPrincipal user,
                                           @PathVariable UUID cycleId) {
        return service.publish(user.organisationId(), cycleId);
    }

    @GetMapping("/{cycleId}/activation")
    public CycleDtos.ActivationDto activation(@AuthenticationPrincipal PmsUserPrincipal user,
                                              @PathVariable UUID cycleId) {
        return service.activation(user.organisationId(), cycleId);
    }

    @PostMapping("/{cycleId}/cancel")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public CycleDtos.CycleDto cancel(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID cycleId) {
        return service.cancel(user.organisationId(), cycleId);
    }
}
