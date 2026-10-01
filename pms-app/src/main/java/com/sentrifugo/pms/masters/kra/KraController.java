package com.sentrifugo.pms.masters.kra;

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

/** Endpoints 1–4 (PMS Configuration contract): the KRA master (screens 2.5/2.6). */
@RestController
@RequestMapping("/pms/kras")
public class KraController {

    private final KraService service;

    public KraController(KraService service) {
        this.service = service;
    }

    @GetMapping
    public List<KraDto> list(@AuthenticationPrincipal PmsUserPrincipal user) {
        return service.list(user.organisationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public KraDto create(@AuthenticationPrincipal PmsUserPrincipal user, @Valid @RequestBody KraUpsertRequest body) {
        return service.create(user.organisationId(), body);
    }

    @PutMapping("/{kraId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public KraDto update(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID kraId,
                         @Valid @RequestBody KraUpsertRequest body) {
        return service.update(user.organisationId(), kraId, body);
    }

    @DeleteMapping("/{kraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public void delete(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID kraId) {
        service.delete(user.organisationId(), kraId, user.id());
    }
}
