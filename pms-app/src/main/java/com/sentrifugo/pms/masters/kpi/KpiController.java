package com.sentrifugo.pms.masters.kpi;

import com.sentrifugo.security.access.RequirePermission;
import com.sentrifugo.security.context.PmsUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints 5–8 (PMS Configuration contract): the KPI master (screens 2.7/2.8). */
@RestController
@RequestMapping("/pms/kpis")
public class KpiController {

    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    @GetMapping
    public List<KpiDto> list(@AuthenticationPrincipal PmsUserPrincipal user) {
        return service.list(user.organisationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public KpiDto create(@AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody KpiUpsertRequest body) {
        return service.create(user.organisationId(), body);
    }

    @PutMapping("/{kpiId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public KpiDto update(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID kpiId,
                         @Valid @RequestBody KpiUpsertRequest body) {
        return service.update(user.organisationId(), kpiId, body);
    }

    @DeleteMapping("/{kpiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public void delete(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID kpiId) {
        service.delete(user.organisationId(), kpiId, user.id());
    }
}
