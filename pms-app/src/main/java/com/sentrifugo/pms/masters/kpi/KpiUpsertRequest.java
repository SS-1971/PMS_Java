package com.sentrifugo.pms.masters.kpi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record KpiUpsertRequest(
        @NotNull UUID kraId,
        @NotBlank @Size(max = 120) String name,
        String unit,
        @NotBlank String targetType,
        @Size(max = 200) String expectedOutcome,
        @Size(max = 200) String evidenceRequired) {
}
