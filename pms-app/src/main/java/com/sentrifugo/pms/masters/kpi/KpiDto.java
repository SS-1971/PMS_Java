package com.sentrifugo.pms.masters.kpi;

import com.sentrifugo.db.config.Kpi;

import java.util.UUID;

public record KpiDto(UUID id, UUID kraId, String kraName, String name, String unit, String targetType,
                     String expectedOutcome, String evidenceRequired) {
    public static KpiDto from(Kpi kpi) {
        return new KpiDto(kpi.getId(), kpi.getKra().getId(), kpi.getKra().getName(), kpi.getName(), kpi.getUnit(),
                kpi.getTargetType().wire(), kpi.getExpectedOutcome(), kpi.getEvidenceRequired());
    }
}
