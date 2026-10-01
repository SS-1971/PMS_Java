package com.sentrifugo.pms.masters.competency;

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

/** Endpoints 10–13 (PMS Configuration contract): the Competency master (screen 2.9). */
@RestController
@RequestMapping("/pms/competencies")
public class CompetencyController {

    private final CompetencyService service;

    public CompetencyController(CompetencyService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompetencyDto> list(@AuthenticationPrincipal PmsUserPrincipal user) {
        return service.list(user.organisationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public CompetencyDto create(@AuthenticationPrincipal PmsUserPrincipal user,
                                @Valid @RequestBody CompetencyUpsertRequest body) {
        return service.create(user.organisationId(), body);
    }

    @PutMapping("/{competencyId}")
    @RequirePermission(module = "performance_management", action = "create_resource")
    public CompetencyDto update(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID competencyId,
                                @Valid @RequestBody CompetencyUpsertRequest body) {
        return service.update(user.organisationId(), competencyId, body);
    }

    @DeleteMapping("/{competencyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(module = "performance_management", action = "create_resource")
    public void delete(@AuthenticationPrincipal PmsUserPrincipal user, @PathVariable UUID competencyId) {
        service.delete(user.organisationId(), competencyId, user.id());
    }
}
