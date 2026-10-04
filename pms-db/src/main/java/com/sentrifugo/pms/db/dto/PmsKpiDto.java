package com.sentrifugo.pms.db.dto;

import java.util.UUID;

public record PmsKpiDto(UUID id, UUID kraId, String kraName, String name, String unit, String targetType,
                        String expectedOutcome, String evidenceRequired) {
}
