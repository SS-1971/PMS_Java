package com.sentrifugo.pms.db.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PmsGoalTemplateCompetencyDto(
        @NotNull UUID competencyId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal weight) {
}
