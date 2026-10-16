package com.sentrifugo.pms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Save draft / submit body for screen 3.14. Ratings are 1-5; a draft may leave any of them null,
 * a submit may not.
 */
public record PmsManagerAppraisalSaveRequest(
        @JsonProperty("employee_user_id") @NotBlank String employeeUserId,
        @JsonProperty("financial_year") @NotBlank @Size(max = 20) String financialYear,
        @JsonProperty("kras") @NotNull @Valid List<Line> kras,
        @JsonProperty("competencies") @NotNull @Valid List<CompetencyLine> competencies,
        @JsonProperty("recommended_rating") Integer recommendedRating,
        @JsonProperty("comments") @Size(max = 500) String comments) {

    public record Line(
            @JsonProperty("kpi_id") @NotNull UUID kpiId,
            @JsonProperty("rating") Integer rating,
            @JsonProperty("comment") @Size(max = 300) String comment) {
    }

    public record CompetencyLine(
            @JsonProperty("id") @NotNull UUID id,
            @JsonProperty("rating") Integer rating,
            @JsonProperty("comment") @Size(max = 300) String comment) {
    }
}
