package com.sentrifugo.pms.db.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PmsGoalTemplateKpiDto(
        @NotNull UUID kpiId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal weight,
        String targetType,
        @Size(max = 200) String expectedOutcome,
        @Size(max = 200) String evidenceRequired) {
}
