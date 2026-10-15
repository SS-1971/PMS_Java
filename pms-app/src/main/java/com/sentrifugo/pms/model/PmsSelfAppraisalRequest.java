package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** The signed-in employee's self appraisal. {@code submit} false keeps it a draft. */
public record PmsSelfAppraisalRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("kras") @NotNull @Valid List<Line> kras,
        @JsonProperty("competencies") @NotNull @Valid List<CompetencyLine> competencies,
        @JsonProperty("submit") boolean submit) {

    public record Line(
            @JsonProperty("kpi_id") @NotNull UUID kpiId,
            @JsonProperty("rating") Integer rating,
            @JsonProperty("attachment_name") @Size(max = 255) String attachmentName,
            /** The employee own actual for the year; empty falls back to the progress figure. */
            @JsonProperty("actual") @jakarta.validation.constraints.PositiveOrZero java.math.BigDecimal actual,
            /** What was achieved, in the employee words. */
            @JsonProperty("evidence") @Size(max = 500) String evidence) {
    }

    public record CompetencyLine(
            @JsonProperty("competency_id") @NotNull UUID competencyId,
            @JsonProperty("rating") Integer rating,
            @JsonProperty("comment") @Size(max = 500) String comment) {
    }
}
