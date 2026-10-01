package com.sentrifugo.pms.masters.kra;

import com.sentrifugo.common.exception.DomainException;
import com.sentrifugo.db.config.Kra;
import com.sentrifugo.db.config.KpiRepository;
import com.sentrifugo.db.config.KraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KraService {

    private final KraRepository kras;
    private final KpiRepository kpis;

    public KraService(KraRepository kras, KpiRepository kpis) {
        this.kras = kras;
        this.kpis = kpis;
    }

    public List<KraDto> list(String organisationId) {
        return kras.findByOrganisationIdAndDeletedOnIsNullOrderByCreatedOnAsc(organisationId).stream()
                .map(KraDto::from).toList();
    }

    @Transactional
    public KraDto create(String organisationId, KraUpsertRequest request) {
        assertNameFree(organisationId, request.name(), null);
        Kra kra = new Kra(organisationId, request.name().trim());
        return KraDto.from(kras.save(kra));
    }

    @Transactional
    public KraDto update(String organisationId, UUID kraId, KraUpsertRequest request) {
        Kra kra = find(organisationId, kraId);
        assertNameFree(organisationId, request.name(), kraId);
        kra.setName(request.name().trim());
        return KraDto.from(kras.save(kra));
    }

    @Transactional
    public void delete(String organisationId, UUID kraId, String actorId) {
        Kra kra = find(organisationId, kraId);
        if (kpis.countByKraIdAndDeletedOnIsNull(kraId) > 0) {
            throw DomainException.conflict(
                    "Cannot delete a KRA that still has KPIs under it.", "PMS_KRA_IN_USE");
        }
        kra.softDelete(actorId);
        kras.save(kra);
    }

    private Kra find(String organisationId, UUID kraId) {
        return kras.findByIdAndOrganisationIdAndDeletedOnIsNull(kraId, organisationId)
                .orElseThrow(() -> DomainException.notFound("KRA not found", "PMS_KRA_NOT_FOUND"));
    }

    private void assertNameFree(String organisationId, String name, UUID excludeId) {
        kras.findDuplicate(organisationId, name.trim(), excludeId).ifPresent(existing -> {
            throw DomainException.conflict(
                    "A KRA named '" + name.trim() + "' already exists.", "PMS_KRA_DUPLICATE");
        });
    }
}
