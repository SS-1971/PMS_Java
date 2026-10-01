package com.sentrifugo.pms.ratingscale;

import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import jakarta.validation.Valid;
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

    private final RatingScaleService service;

    public RatingScaleController(RatingScaleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RatingScaleLookupDto> lookup(@AuthenticationPrincipal PmsUserPrincipal user) {
        return service.listLookup(user.organisationId());
    }

    @GetMapping("/config")
    public List<RatingScaleConfigDto> config(@AuthenticationPrincipal PmsUserPrincipal user) {
        return service.listConfig(user.organisationId());
    }

    @PutMapping("/{scaleId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public RatingScaleConfigDto update(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID scaleId,
                                       @Valid @RequestBody RatingScaleUpdateRequest body) {
        return service.update(user.organisationId(), scaleId, body);
    }
}
