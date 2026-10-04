package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.pms.db.dto.PmsKpiDto;
import com.sentrifugo.pms.db.dto.PmsKpiUpsertDto;
import com.sentrifugo.pms.db.entity.PmsKpiEntity;
import com.sentrifugo.pms.db.entity.PmsKraEntity;
import com.sentrifugo.pms.db.mapper.PmsKpiMapper;
import com.sentrifugo.pms.db.repository.PmsGoalTemplateKpiRepository;
import com.sentrifugo.pms.db.repository.PmsKpiRepository;
import com.sentrifugo.pms.db.repository.PmsKraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KpiService {

    private final PmsKpiRepository kpis;
    private final PmsKraRepository kras;
    private final PmsGoalTemplateKpiRepository goalTemplateKpis;
    private final PmsKpiMapper mapper;

    public KpiService(PmsKpiRepository kpis, PmsKraRepository kras, PmsGoalTemplateKpiRepository goalTemplateKpis,
                      PmsKpiMapper mapper) {
        this.kpis = kpis;
        this.kras = kras;
        this.goalTemplateKpis = goalTemplateKpis;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PmsKpiDto> list(String organisationId) {
        return mapper.toDtoList(kpis.findByOrganisationIdOrderByCreatedDateAsc(organisationId));
    }

    @Transactional
    public PmsKpiDto create(String organisationId, PmsKpiUpsertDto request) {
        PmsKraEntity kra = findKra(organisationId, request.kraId());
        assertNameFree(request.kraId(), request.name(), null);
        return mapper.toDto(kpis.save(mapper.toEntity(request, organisationId, kra)));
    }

    @Transactional
    public PmsKpiDto update(String organisationId, UUID kpiId, PmsKpiUpsertDto request) {
        PmsKpiEntity kpi = find(organisationId, kpiId);
        PmsKraEntity kra = findKra(organisationId, request.kraId());
        assertNameFree(request.kraId(), request.name(), kpiId);
        mapper.updateEntity(request, kra, kpi);
        return mapper.toDto(kpis.save(kpi));
    }

    @Transactional
    public void delete(String organisationId, UUID kpiId) {
        PmsKpiEntity kpi = find(organisationId, kpiId);
        if (goalTemplateKpis.existsByKpiIdAndTemplateKraTemplateIsActiveTrue(kpiId)) {
            throw DomainException.conflict("Cannot delete a KPI used in a goal template.", "PMS_KPI_IN_USE");
        }
        kpis.delete(kpi);
    }

    private PmsKpiEntity find(String organisationId, UUID kpiId) {
        return kpis.findByIdAndOrganisationId(kpiId, organisationId)
                .orElseThrow(() -> DomainException.notFound("KPI not found", "PMS_KPI_NOT_FOUND"));
    }

    private PmsKraEntity findKra(String organisationId, UUID kraId) {
        return kras.findByIdAndOrganisationId(kraId, organisationId)
                .orElseThrow(() -> DomainException.unprocessable(
                        "kra_id does not refer to an existing KRA", "PMS_KRA_NOT_FOUND"));
    }

    private void assertNameFree(UUID kraId, String name, UUID excludeId) {
        kpis.findDuplicate(kraId, name.trim(), excludeId).ifPresent(existing -> {
            throw DomainException.conflict(
                    "A KPI named '" + name.trim() + "' already exists under this KRA.", "PMS_KPI_DUPLICATE");
        });
    }
}
