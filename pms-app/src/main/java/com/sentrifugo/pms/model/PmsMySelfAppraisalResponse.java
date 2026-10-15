package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Screen 4.6: the employee self appraisal sheet. */
public record PmsMySelfAppraisalResponse(
        @JsonProperty("financial_year") String financialYear,
        /** {@code draft} while editable, {@code submitted} once handed to the manager. */
        @JsonProperty("status") String status,
        @JsonProperty("submitted_on") LocalDateTime submittedOn,
        @JsonProperty("self_score") BigDecimal selfScore,
        @JsonProperty("kras") List<Kra> kras,
        @JsonProperty("competencies") List<Competency> competencies) {

    public record Kra(
            @JsonProperty("kpi_id") UUID kpiId,
            @JsonProperty("kra_name") String kraName,
            @JsonProperty("kpi_name") String kpiName,
            @JsonProperty("unit") String unit,
            @JsonProperty("weight") BigDecimal weight,
            /** The target the manager first assigned; never overwritten by a revision. */
            @JsonProperty("target") BigDecimal target,
            /** The final target after a revision; null if never revised. */
            @JsonProperty("revised_target") BigDecimal revisedTarget,
            @JsonProperty("actual") BigDecimal actual,
            @JsonProperty("self_rating") Integer selfRating,
            @JsonProperty("evidence") String evidence,
            @JsonProperty("attachment_name") String attachmentName) {
    }

    public record Competency(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            /** {@code hr} or {@code manager}: who rates it later. */
            @JsonProperty("type") String type,
            @JsonProperty("self_rating") Integer selfRating,
            @JsonProperty("comment") String comment) {
    }
}
