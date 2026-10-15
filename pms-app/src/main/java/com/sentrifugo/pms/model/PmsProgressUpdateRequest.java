package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in employee reports actuals against their approved goals. {@code period} is
 * {@code current}, {@code h1} or {@code fy}.
 */
public record PmsProgressUpdateRequest(
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("period") @NotBlank String period,
        @JsonProperty("entries") @NotNull @Size(min = 1) @Valid List<Entry> entries,
        @JsonProperty("comment") @Size(max = 1000) String comment,
        /** True submits the period to the manager: every KPI needs an actual and a status, and it is then frozen. */
        @JsonProperty("submit") Boolean submit) {

    public record Entry(
            @JsonProperty("kpi_id") @NotNull UUID kpiId,
            @JsonProperty("actual") @NotNull BigDecimal actual,
            @JsonProperty("attachment_name") @Size(max = 255) String attachmentName,
            /** {@code on_track}, {@code at_risk} or {@code off_track}. */
            @JsonProperty("status")
            @jakarta.validation.constraints.Pattern(regexp = "on_track|at_risk|off_track",
                    message = "status must be on_track, at_risk or off_track") String status) {
    }
}
