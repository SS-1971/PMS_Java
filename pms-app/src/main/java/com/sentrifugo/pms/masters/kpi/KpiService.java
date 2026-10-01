package com.sentrifugo.pms.masters.kpi;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.config.GoalTemplateKpiRepository;
import com.sentrifugo.db.config.Kpi;
import com.sentrifugo.db.config.KpiRepository;
import com.sentrifugo.db.config.Kra;
import com.sentrifugo.db.config.KraRepository;
import com.sentrifugo.db.config.TargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KpiService {

    private final KpiRepository kpis;
    private final KraRepository kras;
    private final GoalTemplateKpiRepository goalTemplateKpis;

    public KpiService(KpiRepository kpis, KraRepository kras, GoalTemplateKpiRepository goalTemplateKpis) {
        this.kpis = kpis;
        this.kras = kras;
        this.goalTemplateKpis = goalTemplateKpis;
    }

    public List<KpiDto> list(String organisationId) {
        return kpis.findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(organisationId).stream()
                .map(KpiDto::from).toList();
    }

    @Transactional
    public KpiDto create(String organisationId, KpiUpsertRequest request) {
        Kra kra = findKra(organisationId, request.kraId());
        assertNameFree(request.kraId(), request.name(), null);
        Kpi kpi = new Kpi(organisationId, kra, request.name().trim(), request.unit(),
                TargetType.fromWire(request.targetType()), request.expectedOutcome(), request.evidenceRequired());
        return KpiDto.from(kpis.save(kpi));
    }

    @Transactional
    public KpiDto update(String organisationId, UUID kpiId, KpiUpsertRequest request) {
        Kpi kpi = find(organisationId, kpiId);
        Kra kra = findKra(organisationId, request.kraId());
        assertNameFree(request.kraId(), request.name(), kpiId);
        kpi.setKra(kra);
        kpi.setName(request.name().trim());
        kpi.setUnit(request.unit());
        kpi.setTargetType(TargetType.fromWire(request.targetType()));
        kpi.setExpectedOutcome(request.expectedOutcome());
        kpi.setEvidenceRequired(request.evidenceRequired());
        return KpiDto.from(kpis.save(kpi));
    }

    @Transactional
    public void delete(String organisationId, UUID kpiId, String actorId) {
        Kpi kpi = find(organisationId, kpiId);
        if (goalTemplateKpis.existsByKpiIdAndTemplateKraTemplateDeletedOnIsNull(kpiId)) {
            throw DomainException.conflict("Cannot delete a KPI used in a goal template.", "PMS_KPI_IN_USE");
        }
        kpi.softDelete(actorId);
        kpis.save(kpi);
    }

    private Kpi find(String organisationId, UUID kpiId) {
        return kpis.findByIdAndOrganisationIdAndDeletedOnIsNull(kpiId, organisationId)
                .orElseThrow(() -> DomainException.notFound("KPI not found", "PMS_KPI_NOT_FOUND"));
    }

    private Kra findKra(String organisationId, UUID kraId) {
        return kras.findByIdAndOrganisationIdAndDeletedOnIsNull(kraId, organisationId)
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
