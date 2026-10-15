package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.entity.PmsApprovalWorkflowEntity;
import com.sentrifugo.db.enums.PmsMasterStatus;
import com.sentrifugo.db.repository.PmsApprovalWorkflowRepository;
import com.sentrifugo.db.repository.PmsCycleRepository;
import com.sentrifugo.pms.model.PmsApprovalWorkflowRequest;
import com.sentrifugo.pms.model.PmsApprovalWorkflowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Approval Workflow master: the chains of approvers a cycle can use. */
@Service
@RequiredArgsConstructor
public class PmsApprovalWorkflowService {

    private static final String SEPARATOR = "\n";

    private final PmsApprovalWorkflowRepository repository;
    private final PmsCycleRepository cycleRepository;

    /** The organisation's workflows; a standard one is created the first time so a cycle can always pick one. */
    @Transactional
    public List<PmsApprovalWorkflowResponse> list(String organisationId) {
        List<PmsApprovalWorkflowEntity> rows =
                repository.findByOrganisationIdAndIsActiveTrueOrderByCreatedDateAsc(organisationId);
        if (rows.isEmpty()) {
            repository.save(PmsApprovalWorkflowEntity.builder()
                    .organisationId(organisationId)
                    .name("Standard")
                    .description("Section Head, Functional Head, Plant HR, Corporate HR, GP")
                    .steps(String.join(SEPARATOR, "Section Head", "Functional Head", "Plant HR", "Corporate HR",
                            "GP"))
                    .status(PmsMasterStatus.ACTIVE)
                    .build());
            rows = repository.findByOrganisationIdAndIsActiveTrueOrderByCreatedDateAsc(organisationId);
        }
        return rows.stream().map(PmsApprovalWorkflowService::toResponse).toList();
    }

    @Transactional
    public PmsApprovalWorkflowResponse create(String organisationId, PmsApprovalWorkflowRequest request) {
        String name = request.name().trim();
        if (repository.existsByOrganisationIdAndNameIgnoreCaseAndIsActiveTrue(organisationId, name)) {
            throw DomainException.conflict("An approval workflow named '" + name + "' already exists.",
                    "PMS_WORKFLOW_EXISTS");
        }
        PmsApprovalWorkflowEntity row = repository.save(PmsApprovalWorkflowEntity.builder()
                .organisationId(organisationId)
                .name(name)
                .description(blank(request.description()))
                .steps(join(request.steps()))
                .status(status(request.status()))
                .build());
        return toResponse(row);
    }

    @Transactional
    public PmsApprovalWorkflowResponse update(String organisationId, UUID id, PmsApprovalWorkflowRequest request) {
        PmsApprovalWorkflowEntity row = find(organisationId, id);
        String name = request.name().trim();
        if (repository.existsByOrganisationIdAndNameIgnoreCaseAndIsActiveTrueAndIdNot(organisationId, name, id)) {
            throw DomainException.conflict("An approval workflow named '" + name + "' already exists.",
                    "PMS_WORKFLOW_EXISTS");
        }
        row.setName(name);
        row.setDescription(blank(request.description()));
        row.setSteps(join(request.steps()));
        row.setStatus(status(request.status()));
        return toResponse(repository.save(row));
    }

    @Transactional
    public void delete(String organisationId, UUID id) {
        PmsApprovalWorkflowEntity row = find(organisationId, id);
        if (cycleRepository.existsByOrganisationIdAndApprovalWorkflowId(organisationId, id)) {
            throw DomainException.conflict("This workflow is used by a cycle and cannot be removed.",
                    "PMS_WORKFLOW_IN_USE");
        }
        row.setIsActive(false);
        repository.save(row);
    }

    /** A workflow a cycle names must be an active one of the same organisation. */
    public void requireUsable(String organisationId, UUID id) {
        if (id == null) {
            return;
        }
        PmsApprovalWorkflowEntity row = find(organisationId, id);
        if (row.getStatus() != PmsMasterStatus.ACTIVE) {
            throw DomainException.unprocessable("The selected approval workflow is inactive.",
                    "PMS_WORKFLOW_INACTIVE");
        }
    }

    private PmsApprovalWorkflowEntity find(String organisationId, UUID id) {
        return repository.findByIdAndOrganisationId(id, organisationId)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> DomainException.notFound("Approval workflow not found", "PMS_WORKFLOW_NOT_FOUND"));
    }

    private static PmsMasterStatus status(String value) {
        return value != null && value.trim().equalsIgnoreCase("inactive") ? PmsMasterStatus.INACTIVE
                : PmsMasterStatus.ACTIVE;
    }

    private static String join(List<String> steps) {
        return String.join(SEPARATOR, steps.stream().map(String::trim).toList());
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PmsApprovalWorkflowResponse toResponse(PmsApprovalWorkflowEntity row) {
        return new PmsApprovalWorkflowResponse(row.getId(), row.getName(), row.getDescription(),
                Arrays.asList(row.getSteps().split(SEPARATOR)), row.getStatus().name().toLowerCase(Locale.ROOT));
    }
}
