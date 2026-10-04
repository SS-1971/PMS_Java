package com.sentrifugo.pms.service;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.pms.db.dto.PmsKraDto;
import com.sentrifugo.pms.db.dto.PmsKraUpsertDto;
import com.sentrifugo.pms.db.entity.PmsKraEntity;
import com.sentrifugo.pms.db.mapper.PmsKraMapper;
import com.sentrifugo.pms.db.repository.PmsKpiRepository;
import com.sentrifugo.pms.db.repository.PmsKraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KraService {

    private final PmsKraRepository kras;
    private final PmsKpiRepository kpis;
    private final PmsKraMapper mapper;

    public KraService(PmsKraRepository kras, PmsKpiRepository kpis, PmsKraMapper mapper) {
        this.kras = kras;
        this.kpis = kpis;
        this.mapper = mapper;
    }

    public List<PmsKraDto> list(String organisationId) {
        return mapper.toDtoList(kras.findByOrganisationIdOrderByCreatedDateAsc(organisationId));
    }

    @Transactional
    public PmsKraDto create(String organisationId, PmsKraUpsertDto request) {
        assertNameFree(organisationId, request.name(), null);
        return mapper.toDto(kras.save(mapper.toEntity(request, organisationId)));
    }

    @Transactional
    public PmsKraDto update(String organisationId, UUID kraId, PmsKraUpsertDto request) {
        PmsKraEntity kra = find(organisationId, kraId);
        assertNameFree(organisationId, request.name(), kraId);
        mapper.updateEntity(request, kra);
        return mapper.toDto(kras.save(kra));
    }

    @Transactional
    public void delete(String organisationId, UUID kraId) {
        PmsKraEntity kra = find(organisationId, kraId);
        if (kpis.countByKraId(kraId) > 0) {
            throw DomainException.conflict(
                    "Cannot delete a KRA that still has KPIs under it.", "PMS_KRA_IN_USE");
        }
        kras.delete(kra);
    }

    private PmsKraEntity find(String organisationId, UUID kraId) {
        return kras.findByIdAndOrganisationId(kraId, organisationId)
                .orElseThrow(() -> DomainException.notFound("KRA not found", "PMS_KRA_NOT_FOUND"));
    }

    private void assertNameFree(String organisationId, String name, UUID excludeId) {
        kras.findDuplicate(organisationId, name.trim(), excludeId).ifPresent(existing -> {
            throw DomainException.conflict(
                    "A KRA named '" + name.trim() + "' already exists.", "PMS_KRA_DUPLICATE");
        });
    }
}
