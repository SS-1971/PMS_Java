package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.pms.db.dto.PmsCompetencyDto;
import com.sentrifugo.pms.db.dto.PmsCompetencyUpsertDto;
import com.sentrifugo.pms.db.entity.PmsCompetencyEntity;
import com.sentrifugo.pms.db.mapper.PmsCompetencyMapper;
import com.sentrifugo.pms.db.repository.PmsCompetencyRepository;
import com.sentrifugo.pms.db.repository.PmsGoalTemplateCompetencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CompetencyService {

    private final PmsCompetencyRepository competencies;
    private final PmsGoalTemplateCompetencyRepository goalTemplateCompetencies;
    private final PmsCompetencyMapper mapper;

    public CompetencyService(PmsCompetencyRepository competencies,
                             PmsGoalTemplateCompetencyRepository goalTemplateCompetencies,
                             PmsCompetencyMapper mapper) {
        this.competencies = competencies;
        this.goalTemplateCompetencies = goalTemplateCompetencies;
        this.mapper = mapper;
    }

    public List<PmsCompetencyDto> list(String organisationId) {
        return mapper.toDtoList(competencies.findByOrganisationIdOrderByCreatedDateAsc(organisationId));
    }

    @Transactional
    public PmsCompetencyDto create(String organisationId, PmsCompetencyUpsertDto request) {
        assertNameFree(organisationId, request.name(), null);
        return mapper.toDto(competencies.save(mapper.toEntity(request, organisationId)));
    }

    @Transactional
    public PmsCompetencyDto update(String organisationId, UUID competencyId, PmsCompetencyUpsertDto request) {
        PmsCompetencyEntity competency = find(organisationId, competencyId);
        assertNameFree(organisationId, request.name(), competencyId);
        mapper.updateEntity(request, competency);
        return mapper.toDto(competencies.save(competency));
    }

    @Transactional
    public void delete(String organisationId, UUID competencyId) {
        PmsCompetencyEntity competency = find(organisationId, competencyId);
        if (goalTemplateCompetencies.existsByCompetencyIdAndTemplateIsActiveTrue(competencyId)) {
            throw DomainException.conflict(
                    "Cannot delete a competency used in a goal template.", "PMS_COMPETENCY_IN_USE");
        }
        competencies.delete(competency);
    }

    private PmsCompetencyEntity find(String organisationId, UUID competencyId) {
        return competencies.findByIdAndOrganisationId(competencyId, organisationId)
                .orElseThrow(() -> DomainException.notFound("Competency not found", "PMS_COMPETENCY_NOT_FOUND"));
    }

    private void assertNameFree(String organisationId, String name, UUID excludeId) {
        competencies.findDuplicate(organisationId, name.trim(), excludeId).ifPresent(existing -> {
            throw DomainException.conflict(
                    "A competency named '" + name.trim() + "' already exists.", "PMS_COMPETENCY_DUPLICATE");
        });
    }
}
