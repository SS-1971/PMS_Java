package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Screen 4.4: the employee progress sheet for one period, with the targets it is measured against. */
public record PmsMyProgressResponse(
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("period") String period,
        /** {@code open} while the employee can edit it, {@code frozen} once submitted to the manager. */
        @JsonProperty("state") String state,
        @JsonProperty("submitted_on") LocalDateTime submittedOn,
        @JsonProperty("comment") String comment,
        @JsonProperty("kras") List<Kra> kras) {

    public record Kra(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("kpi_name") String kpiName,
            @JsonProperty("unit") String unit,
            @JsonProperty("weight") BigDecimal weight,
            /** The target the manager first assigned; revisions never change it. */
            @JsonProperty("target") BigDecimal target,
            /** The final target after a revision, shown only in the Revised target column; null if never revised. */
            @JsonProperty("revised_target") BigDecimal revisedTarget,
            @JsonProperty("actual") BigDecimal actual,
            @JsonProperty("status") String status,
            @JsonProperty("evidence_name") String evidenceName) {
    }
}
