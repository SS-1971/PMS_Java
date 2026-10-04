package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.db.dto.PmsKraDto;
import com.sentrifugo.pms.db.dto.PmsKraUpsertDto;
import com.sentrifugo.pms.service.KraService;
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

/** Endpoints 1–4 (PMS Configuration contract): the KRA master (screens 2.5/2.6). */
@RestController
@RequestMapping("/pms/kras")
public class KraController {

    private static final Logger log = LoggerFactory.getLogger(KraController.class);

    private final KraService service;

    public KraController(KraService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List KRAs",
            description = "Retrieves all Key Result Areas defined for the authenticated organisation.")
    public ResponseEntity<ApiResponse<List<PmsKraDto>>> list(@AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching KRAs for organisation: {}", user.organisationId());
        List<PmsKraDto> kras = service.list(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(kras, String.format("Found %d KRAs", kras.size())));
    }

    @PostMapping
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Create a KRA",
            description = "Creates a Key Result Area in the authenticated organisation; names must be unique per organisation.")
    public ResponseEntity<ApiResponse<PmsKraDto>> create(@AuthenticationPrincipal PmsUserPrincipal user,
                                                         @Valid @RequestBody PmsKraUpsertDto body) {
        log.info("Creating KRA for organisation: {}", user.organisationId());
        PmsKraDto kra = service.create(user.organisationId(), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(kra, "KRA created successfully"));
    }

    @PutMapping("/{kraId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Update a KRA",
            description = "Renames an existing Key Result Area belonging to the authenticated organisation.")
    public ResponseEntity<ApiResponse<PmsKraDto>> update(@AuthenticationPrincipal PmsUserPrincipal user,
                                                         @PathVariable UUID kraId,
                                                         @Valid @RequestBody PmsKraUpsertDto body) {
        log.info("Updating KRA: {}", kraId);
        PmsKraDto kra = service.update(user.organisationId(), kraId, body);
        return ResponseEntity.ok(ApiResponse.ok(kra, "KRA updated successfully"));
    }

    @DeleteMapping("/{kraId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Delete a KRA",
            description = "Soft-deletes a Key Result Area; rejected while KPIs still exist under it.")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal PmsUserPrincipal user,
                                                    @PathVariable UUID kraId) {
        log.info("Deleting KRA: {}", kraId);
        service.delete(user.organisationId(), kraId);
        return ResponseEntity.ok(ApiResponse.ok("KRA deleted successfully"));
    }
}
