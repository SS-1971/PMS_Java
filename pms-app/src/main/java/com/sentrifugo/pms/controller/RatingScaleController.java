package com.sentrifugo.pms.controller;

import com.sentrifugo.common.web.ApiResponse;
import com.sentrifugo.pms.db.dto.PmsRatingScaleDto;
import com.sentrifugo.pms.db.dto.PmsRatingScaleLookupDto;
import com.sentrifugo.pms.model.ratingscale.RatingScaleUpdateRequest;
import com.sentrifugo.pms.service.RatingScaleService;
import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoint 12 (PMS Cycle contract, trimmed lookup) and 14–15
 * (PMS Configuration contract, full config) — same {@code pms_rating_scales}
 * table, two read shapes plus the one write path (screen 2.10).
 */
@RestController
@RequestMapping("/pms/rating-scales")
public class RatingScaleController {

    private static final Logger log = LoggerFactory.getLogger(RatingScaleController.class);

    private final RatingScaleService service;

    public RatingScaleController(RatingScaleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get rating scale lookup",
            description = "Retrieves the trimmed rating-scale list (id, name, level values/labels) used by the cycle wizard dropdown.")
    public ResponseEntity<ApiResponse<List<PmsRatingScaleLookupDto>>> lookup(
            @AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching rating scale lookup for organisation: {}", user.organisationId());
        List<PmsRatingScaleLookupDto> ratingScales = service.listLookup(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(ratingScales,
                String.format("Found %d rating scales", ratingScales.size())));
    }

    @GetMapping("/config")
    @Operation(summary = "Get rating scale configuration",
            description = "Retrieves every rating scale with its full level definitions (labels, score ranges, colours) for the configuration screen.")
    public ResponseEntity<ApiResponse<List<PmsRatingScaleDto>>> config(
            @AuthenticationPrincipal PmsUserPrincipal user) {
        log.info("Fetching rating scale configuration for organisation: {}", user.organisationId());
        List<PmsRatingScaleDto> ratingScales = service.listConfig(user.organisationId());
        return ResponseEntity.ok(ApiResponse.ok(ratingScales,
                String.format("Found %d rating scales", ratingScales.size())));
    }

    @PutMapping("/{scaleId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    @Operation(summary = "Update a rating scale",
            description = "Updates the level labels, score ranges and colours of an existing rating scale, plus its default and definition-visibility flags.")
    public ResponseEntity<ApiResponse<PmsRatingScaleDto>> update(@AuthenticationPrincipal PmsUserPrincipal user,
                                                                 @PathVariable UUID scaleId,
                                                                 @Valid @RequestBody RatingScaleUpdateRequest body) {
        log.info("Updating rating scale: {}", scaleId);
        PmsRatingScaleDto ratingScale = service.update(user.organisationId(), scaleId, body);
        return ResponseEntity.ok(ApiResponse.ok(ratingScale, "Rating scale updated successfully"));
    }
}
