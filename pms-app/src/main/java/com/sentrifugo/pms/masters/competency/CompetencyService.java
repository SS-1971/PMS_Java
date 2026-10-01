package com.sentrifugo.pms.masters.competency;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.config.Competency;
import com.sentrifugo.db.config.CompetencyCategory;
import com.sentrifugo.db.config.CompetencyRepository;
import com.sentrifugo.db.config.GoalTemplateCompetencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CompetencyService {

    private final CompetencyRepository competencies;
    private final GoalTemplateCompetencyRepository goalTemplateCompetencies;

    public CompetencyService(CompetencyRepository competencies,
                             GoalTemplateCompetencyRepository goalTemplateCompetencies) {
        this.competencies = competencies;
        this.goalTemplateCompetencies = goalTemplateCompetencies;
    }

    public List<CompetencyDto> list(String organisationId) {
        return competencies.findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(organisationId).stream()
                .map(CompetencyDto::from).toList();
    }

    @Transactional
    public CompetencyDto create(String organisationId, CompetencyUpsertRequest request) {
        assertNameFree(organisationId, request.name(), null);
        Competency competency = new Competency(organisationId, request.name().trim(),
                CompetencyCategory.fromWire(request.category()), request.isActive() == null || request.isActive());
        return CompetencyDto.from(competencies.save(competency));
    }

    @Transactional
    public CompetencyDto update(String organisationId, UUID competencyId, CompetencyUpsertRequest request) {
        Competency competency = find(organisationId, competencyId);
        assertNameFree(organisationId, request.name(), competencyId);
        competency.setName(request.name().trim());
        competency.setCategory(CompetencyCategory.fromWire(request.category()));
        if (request.isActive() != null) {
            competency.setActive(request.isActive());
        }
        return CompetencyDto.from(competencies.save(competency));
    }

    @Transactional
    public void delete(String organisationId, UUID competencyId, String actorId) {
        Competency competency = find(organisationId, competencyId);
        if (goalTemplateCompetencies.existsByCompetencyIdAndTemplateDeletedOnIsNull(competencyId)) {
            throw DomainException.conflict(
                    "Cannot delete a competency used in a goal template.", "PMS_COMPETENCY_IN_USE");
        }
        competency.softDelete(actorId);
        competencies.save(competency);
    }

    private Competency find(String organisationId, UUID competencyId) {
        return competencies.findByIdAndOrganisationIdAndDeletedOnIsNull(competencyId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Competency not found", "PMS_COMPETENCY_NOT_FOUND"));
    }

    private void assertNameFree(String organisationId, String name, UUID excludeId) {
        competencies.findDuplicate(organisationId, name.trim(), excludeId).ifPresent(existing -> {
            throw DomainException.conflict(
                    "A competency named '" + name.trim() + "' already exists.", "PMS_COMPETENCY_DUPLICATE");
        });
    }
}
