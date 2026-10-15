package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Screen 2.5 (Competency Configuration): the ticked competencies. A weight is optional: when any is left out the
 * competencies share 100 equally; when given, the weights must total 100.
 */
public record PmsGoalTemplateCompetencyRequest(
        @JsonProperty("competencies") @NotNull List<@Valid Item> competencies) {

    public record Item(
            @JsonProperty("competency_id") @NotNull UUID competencyId,
            @JsonProperty("weightage") @DecimalMin(value = "0.00", inclusive = false)
            @DecimalMax("100.00") BigDecimal weightage) {
    }
}
